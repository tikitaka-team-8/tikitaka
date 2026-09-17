"""Local authenticated S09 VU steps. No login traffic inside measured intervals."""
import argparse
import atexit
import base64
import datetime as dt
import hashlib
import hmac
import json
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import sys
import re

# Temporary per-process request; do not change the user's persistent power plan.
if sys.platform == 'win32':
    import ctypes
    execution_state = ctypes.windll.kernel32.SetThreadExecutionState
    execution_state.argtypes = [ctypes.c_uint]
    execution_state.restype = ctypes.c_uint
    if not execution_state(0x80000003):
        raise RuntimeError('Cannot request temporary idle-sleep prevention')
    atexit.register(lambda: execution_state(0x80000000))

ROOT = Path(__file__).resolve().parents[3]
SCENARIO = Path(__file__).resolve().parent
K6 = 'grafana/k6@sha256:5221b620a4f874faff6e32ba597aa667c058391fe4898b1c6f6377f062c6cdec'
parser = argparse.ArgumentParser()
parser.add_argument('--vus', nargs='+', type=int, default=[50, 100, 300, 500, 1000])
parser.add_argument('--seconds', type=int, default=120)
parser.add_argument('--poll-seconds', type=int, choices=[1, 2, 5], default=2)
parser.add_argument('--mode', choices=['vu', 'spike', 'soak'], default='vu')
parser.add_argument('--restart', choices=['none', 'gateway', 'apps'], default='none',
                    help='Optional controlled restart; infrastructure stays running')
args = parser.parse_args()
if args.mode == 'spike':
    args.vus, args.seconds = [1000], 120
elif args.mode == 'soak':
    args.vus, args.seconds = [300], 600
if any(v not in [50, 100, 300, 500, 1000] for v in args.vus) or (args.mode == 'vu' and args.seconds != 120):
    raise SystemExit('This runner is bounded to the planned VU levels and 120 second steps')
OUT = ROOT / 'artifacts/queue-load' / (dt.datetime.now().strftime('%Y%m%d-%H%M%S') + '-s09-' + args.mode)
OUT.mkdir(parents=True)


def cmd(argv, data=None):
    p = subprocess.run(argv, input=data, capture_output=True, text=True, encoding='utf-8', timeout=30)
    if p.returncode:
        raise RuntimeError('Command failed: ' + argv[0])
    return p.stdout.strip()


def redis(*arguments):
    return cmd(['docker', 'exec', 'tikitaka-ticketing-redis', 'redis-cli', '--raw', *map(str, arguments)])


def sql(container, statement):
    return cmd(['docker', 'exec', '-i', container, 'sh', '-c',
                'psql -X -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], statement)


def save(path, value):
    path.write_text(json.dumps(value, indent=2), encoding='utf-8')


def get(url):
    with urllib.request.urlopen(url, timeout=8) as r:
        return r.read().decode()


def metrics(port):
    raw = get(f'http://127.0.0.1:{port}/actuator/prometheus')
    return {line.rsplit(' ', 1)[0]: float(line.rsplit(' ', 1)[1]) for line in raw.splitlines()
            if line and not line.startswith('#') and not line.endswith('NaN')}


def snapshot(sid, k6name=None):
    targets = ['tikitaka-gateway', 'tikitaka-ticketing-service', 'tikitaka-platform-service', 'tikitaka-ticketing-redis']
    if k6name:
        targets.append(k6name)
    p = subprocess.run(['docker', 'stats', '--no-stream', '--format', '{{json .}}', *targets],
                       capture_output=True, text=True, encoding='utf-8', timeout=20)
    stats = [json.loads(line) for line in p.stdout.splitlines() if line.startswith('{')]
    m, g = metrics(8082), metrics(8000)
    info = redis('INFO', 'stats') + '\n' + redis('INFO', 'memory') + '\n' + redis('INFO', 'commandstats')
    with socket.create_connection(('127.0.0.1', 6381), timeout=3) as connection:
        t = time.perf_counter()
        connection.sendall(b'*1\r\n$4\r\nPING\r\n')
        if connection.recv(64) != b'+PONG\r\n':
            raise RuntimeError('Redis PING failed')
        ping_ms = (time.perf_counter() - t) * 1000
    return dict(at=time.time(), waiting=int(redis('ZCARD', 'queue:waiting:{' + sid + '}')),
                active=int(redis('ZCARD', 'queue:active:{' + sid + '}')),
                ticketingCpu=m.get('process_cpu_usage'), gatewayCpu=g.get('process_cpu_usage'),
                heapBytes=sum(v for k,v in m.items() if k.startswith('jvm_memory_used_bytes{') and 'area="heap"' in k),
                admission=m.get('queue_admission_total'), heartbeatExpired=m.get('queue_heartbeat_expired_total'),
                docker=stats, redisInfo=info, redisPingMs=ping_ms)


def fixture():
    sid, seat = str(uuid.uuid4()), str(uuid.uuid4())
    r = sql('tikitaka-platform-postgres', f"""INSERT INTO p_event_session
    (id,event_id,session_number,performance_start_at,performance_end_at,sales_open_at,sales_close_at,status,queue_enabled)
    SELECT '{sid}',s.event_id,(SELECT COALESCE(MAX(session_number),0)+1 FROM p_event_session WHERE event_id=s.event_id),
    now()+interval '2 days',now()+interval '2 days 2 hours',now()-interval '1 hour',now()+interval '1 day','SCHEDULED',true
    FROM p_event_session s WHERE s.id='31000000-0000-0000-0000-000000000001' RETURNING id;""")
    if sid not in r:
        raise RuntimeError('Template session missing')
    sql('tikitaka-ticketing-postgres', f"""INSERT INTO p_schedule_seat
    (schedule_seat_id,event_session_id,venue_seat_id,section,row_label,seat_number,seat_grade,price,seat_status,created_by,updated_by)
    VALUES ('{seat}','{sid}','{uuid.uuid4()}','S09VU','A','1','VIP',10000,'AVAILABLE',1,1);""")
    return sid, seat


def cleanup(sid):
    keys = redis('--scan', '--pattern', 'queue:*{' + str(uuid.UUID(sid)) + '}*').splitlines()
    for offset in range(0, len(keys), 100):
        redis('DEL', *keys[offset:offset+100])
    redis('SREM', 'queue:waiting-sessions', sid)
    redis('SREM', 'queue:active-sessions', sid)


def token(user_id, key):
    def enc(value):
        return base64.urlsafe_b64encode(value).rstrip(b'=')
    now = int(time.time())
    content = enc(b'{"alg":"HS256"}') + b'.' + enc(json.dumps({
        'sub': str(user_id), 'iat': now, 'exp': now+900, 'tokenType': 'access', 'role': 'USER'}).encode())
    return (content + b'.' + enc(hmac.new(key, content, hashlib.sha256).digest())).decode()


def verify(sid, ids):
    evidence = []
    for offset in range(0, len(ids), 300):
        # Only hashes of tokens leave Redis; tokens may expire naturally after three minutes.
        lua = """local out={};for i=2,#ARGV do
          local e='queue:entry:'..ARGV[1]..':'..ARGV[i]
          local seq=redis.call('HGET',e,'sequence')
          local token=redis.call('GET','queue:admission-token-ref:'..ARGV[1]..':'..ARGV[i])
          local tokenState='EXPIRED_OR_ABSENT'
          if token then tokenState=redis.call('HGET','queue:admission-token:'..ARGV[1]..':'..token,'status') or 'MISSING' end
          table.insert(out,{user=ARGV[i],state=redis.call('HGET',e,'status') or 'MISSING',
           sequence=seq or '',tokenHash=token and redis.sha1hex(token) or '',tokenState=tokenState}) end
          return cjson.encode(out)"""
        evidence += json.loads(redis('EVAL', lua, 0, '{'+sid+'}', *ids[offset:offset+300]))
    hashes = [r['tokenHash'] for r in evidence if r['tokenHash']]
    valid = (all(r['state']=='ENTERED' and r['sequence'] and r['tokenState'] in ['USED','EXPIRED_OR_ABSENT'] for r in evidence)
             and len(set(r['sequence'] for r in evidence)) == len(ids)
             and len(set(hashes)) == len(hashes) and int(redis('GET','queue:sequence:{'+sid+'}')) == len(ids))
    return dict(valid=valid, users=len(ids), presentTokenRefs=len(hashes), entries=evidence)


QUERIES = {
 'up': 'up{job=~"gateway|ticketing-service"}',
 'requests': 'sum by(job,uri,method,status)(rate(http_server_requests_seconds_count{job=~"gateway|ticketing-service",uri!~"/actuator.*"}[1m]))',
 'waiting': 'queue_waiting_count{job="ticketing-service"}',
 'admission': 'rate(queue_admission_total{job="ticketing-service"}[1m])',
 'scheduler': 'rate(queue_scheduler_duration_seconds_sum{job="ticketing-service"}[1m])/rate(queue_scheduler_duration_seconds_count{job="ticketing-service"}[1m])',
 'cpu': 'process_cpu_usage{job=~"gateway|ticketing-service"}',
 'heap': 'sum by(job)(jvm_memory_used_bytes{job=~"gateway|ticketing-service",area="heap"})',
 'gcPause': 'sum by(job)(rate(jvm_gc_pause_seconds_sum{job=~"gateway|ticketing-service"}[1m]))',
 'gcCount': 'sum by(job)(rate(jvm_gc_pause_seconds_count{job=~"gateway|ticketing-service"}[1m]))'
}


print('Results:', OUT, flush=True)
startup = {'restart': args.restart, 'startedAt': time.time(), 'attempts': []}
save(OUT/'startup.json', startup)
try:
    restart_targets = {'none': [], 'gateway': ['tikitaka-gateway'],
                       'apps': ['tikitaka-platform-service', 'tikitaka-ticketing-service', 'tikitaka-gateway']}[args.restart]
    if restart_targets:
        cmd(['docker', 'restart', *restart_targets])
    deadline = time.monotonic() + 90
    while True:
        ready = True
        for port in [8000, 8081, 8082]:
            probe = {'port': port, 'at': time.time()}
            try:
                with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health', timeout=2) as response:
                    body = json.load(response)
                    probe.update(httpStatus=response.status, healthStatus=body.get('status'))
                    ok = response.status == 200 and body.get('status') == 'UP'
            except Exception as error:
                probe.update(errorType=type(error).__name__, error=str(error))
                if isinstance(error, urllib.error.HTTPError):
                    probe['httpStatus'] = error.code
                ok = False
            probe['ready'] = ok
            startup['attempts'].append(probe)
            ready = ready and ok
        save(OUT/'startup.json', startup)
        if ready:
            startup['readyAt'] = time.time()
            save(OUT/'startup.json', startup)
            break
        if time.monotonic() >= deadline:
            raise RuntimeError('Application health not UP; no fixtures or load started. See startup.json')
        time.sleep(1)
except Exception as error:
    startup['failure'] = str(error)
    save(OUT/'startup.json', startup)
    raise
containers = json.loads(cmd(['docker','inspect','tikitaka-gateway','tikitaka-ticketing-service','tikitaka-platform-service']))
def env(c):
    return dict(v.split('=',1) for v in c['Config']['Env'])
if env(containers[0])['JWT_SECRET'] != env(containers[2])['JWT_SECRET']:
    raise RuntimeError('Gateway/Platform JWT configuration mismatch')
secret_key = base64.b64decode(env(containers[0])['JWT_SECRET'])
save(OUT/'environment.json', {'branch':cmd(['git','branch','--show-current']), 'commit':cmd(['git','rev-parse','HEAD']),
 'authentication':'Local fixture JWT, HS256 same claims, 15 minute TTL; real Gateway validation; Auth endpoints excluded',
 'images':[{'name':c['Name'],'image':c['Image'],'labels':c['Config'].get('Labels'),'restartCount':c['RestartCount'], 'startedAt':c['State']['StartedAt']} for c in containers],
 'restart':args.restart,'healthReadyAt':startup['readyAt'],
 'mode':args.mode,'vus':args.vus,'seconds':args.seconds,'pollSeconds':args.poll_seconds,'heartbeatSeconds':15,'thinkSeconds':0.2,'k6':K6})
for source in [Path(__file__), SCENARIO/'queue-vu.js']:
    (OUT/('executed-'+source.name)).write_bytes(source.read_bytes())
prefix = 's09vu-' + uuid.uuid4().hex[:12]
user_base = int(time.time()*1000)*100
pool_size = 40000 if args.mode == 'soak' else (1000 if args.mode == 'spike' else 12000)
user_ids = [int(line) for line in sql('tikitaka-platform-postgres',f"""INSERT INTO p_user(id,email,password_hash,name,nickname,role,status)
 SELECT {user_base}+i,'{prefix}-'||i||'@test.tikitaka.local','fixture-not-for-password-login','S09 VU','{prefix}-'||i,'USER','ACTIVE'
 FROM generate_series(1,{pool_size}) i RETURNING id;""").splitlines() if line.isdigit()]
if len(user_ids) != pool_size:
    raise RuntimeError('Fixture user pool incomplete')
save(OUT/'users.json', {'prefix':prefix, 'userIds':user_ids})
for vus in args.vus:
    folder = OUT/str(vus)
    folder.mkdir()
    sid, seat = fixture()
    save(folder/'fixture.json',dict(sessionId=sid,seatId=seat))
    try:
        before = snapshot(sid)
        save(folder/'baseline.json', before)
        # Verify the new offline fixture auth path once before any load; no fake user headers.
        req = urllib.request.Request('http://127.0.0.1:8000/api/v1/event-sessions/'+sid+'/queue/me',
                                    headers={'Authorization':'Bearer '+token(user_ids[0],secret_key)})
        try:
            urllib.request.urlopen(req,timeout=5)
            raise RuntimeError('New fixture unexpectedly exists')
        except urllib.error.HTTPError as e:
            if e.code != 404 or json.loads(e.read()).get('code') != 'Q-002':
                raise RuntimeError('Fixture JWT authentication failed')
        with tempfile.TemporaryDirectory(prefix='private-',dir=folder) as private:
            save(Path(private)/'users.json',[{'userId':i,'accessToken':token(i,secret_key)} for i in user_ids])
            name = 's09-vu-'+str(vus)+'-'+sid[:8]
            command = ['docker','run','--rm','--name',name,'--network','tikitaka-network',
                '--mount',f'type=bind,source={SCENARIO},target=/scripts,readonly',
                '--mount',f'type=bind,source={private},target=/private,readonly',
                '--mount',f'type=bind,source={folder},target=/results',
                '-e','SESSION_ID='+sid,'-e','SEAT_ID='+seat,'-e','VUS='+str(vus),'-e','SECONDS='+str(args.seconds),'-e','MODE='+args.mode,'-e','POLL_SECONDS='+str(args.poll_seconds),
                '-e','K6_WEB_DASHBOARD=true','-e','K6_WEB_DASHBOARD_PERIOD=2s','-e','K6_WEB_DASHBOARD_PORT=-1',
                '-e','K6_WEB_DASHBOARD_EXPORT=/results/report.html',K6,'run','--quiet','--out','json=/results/metrics.json','/scripts/queue-vu.js']
            start = time.time()
            save(folder/'timing.json',dict(launch=start,healthReadyAt=startup['readyAt'],readyToLoadSeconds=start-startup['readyAt']))
            print('START VUs',vus,flush=True)
            with (folder/'k6.log').open('w',encoding='utf-8') as log:
                process = subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT)
                samples=[]
                try:
                    while process.poll() is None:
                        time.sleep(5)
                        sample=snapshot(sid,name if process.poll() is None else None)
                        samples.append(sample)
                        save(folder/'samples.json',samples)
                        print('VU',vus,'elapsed',round(time.time()-start),'waiting',sample['waiting'],'active',sample['active'],flush=True)
                except Exception:
                    cmd(['docker','stop','--time','5',name])
                    process.wait(timeout=15)
                    raise
            end=time.time()
            save(folder/'timing.json',dict(launch=start,processEnd=end,healthReadyAt=startup['readyAt'],readyToLoadSeconds=start-startup['readyAt']))
            if process.returncode != 0:
                raise RuntimeError('k6 checks failed; see saved k6.log; higher VU steps cancelled')
        summary=json.loads((folder/'k6-summary.json').read_text(encoding='utf-8-sig'))['metrics']
        started=int(summary['users_started']['values']['count'])
        completed=int(summary['normal_journey_completed']['values']['count'])
        if args.mode != 'vu':
            evidence=[]
            for line in (folder/'k6.log').read_text(encoding='utf-8').splitlines():
                if 'TOKEN_EVIDENCE ' in line:
                    # k6 text logs quote and escape the JSON message.
                    match=re.search(r'msg=("(?:[^"\\]|\\.)*")',line)
                    message=json.loads(match.group(1)) if match else line
                    evidence.append(json.JSONDecoder().raw_decode(message.split('TOKEN_EVIDENCE ',1)[1])[0])
            valid=(len(evidence)==completed and len({e['tokenHash'] for e in evidence})==completed
                   and len({e['userId'] for e in evidence})==completed
                   and all(e['sessionId']==sid for e in evidence))
            save(folder/'token-evidence.json',dict(valid=valid,count=len(evidence),entries=evidence))
            if not valid:
                raise RuntimeError('Live token fingerprint evidence incomplete or duplicate')
        integrity=verify(sid,user_ids[:started])
        save(folder/'integrity.json',integrity)
        if not integrity['valid'] or started != completed:
            raise RuntimeError('Incomplete journey or Redis integrity failure; higher steps cancelled')
        recovery=[]
        # Operational gate, not team latency/TPS SLO. Record raw heap rather than require GC at an exact moment.
        cpu_limit=max(0.10,(before['ticketingCpu'] or 0)+0.05)
        gateway_limit=max(0.10,(before['gatewayCpu'] or 0)+0.05)
        stable=0
        for _ in range(18):
            sample=snapshot(sid)
            recovery.append(sample)
            stable = stable+1 if sample['waiting']==0 and sample['active']==0 and sample['ticketingCpu']<=cpu_limit and sample['gatewayCpu']<=gateway_limit else 0
            save(folder/'recovery.json',dict(samples=recovery,stable=stable,cpuLimit=cpu_limit,gatewayCpuLimit=gateway_limit))
            if stable>=3:
                break
            time.sleep(5)
        if stable<3:
            raise RuntimeError('Queue/CPU recovery gate not met; higher steps cancelled')
        after=json.loads(cmd(['docker','inspect','tikitaka-gateway','tikitaka-ticketing-service','tikitaka-platform-service']))
        if any(c['RestartCount']!=old['RestartCount'] or c['State'].get('OOMKilled') or not c['State']['Running'] for c,old in zip(after,containers)):
            raise RuntimeError('Restart/OOM/stopped service detected; higher steps cancelled')
        ranges={}
        for label,query in QUERIES.items():
            ranges[label]=json.loads(get('http://127.0.0.1:9090/api/v1/query_range?'+urllib.parse.urlencode(dict(query=query,start=start,end=time.time(),step=5))))
        save(folder/'prometheus.json',ranges)
        result=dict(vus=vus,started=started,completed=completed,requests=summary['http_reqs']['values']['count'],
                    failed=summary['http_req_failed']['values']['rate'],wait=summary['admission_observed_wait_ms']['values'],
                    http=summary['http_req_duration']['values'],waitingSampleMax=max(s['waiting'] for s in samples),
                    postProcessRecoverySeconds=recovery[-1]['at']-end)
        save(folder/'result.json',result)
        print('COMPLETE',json.dumps(result),flush=True)
    except Exception as error:
        save(folder/'stopped.json',dict(reason=str(error),at=time.time()))
        # Preserve failure evidence before fixture cleanup, including unfinished entries.
        try:
            save(folder/'failure-snapshot.json',snapshot(sid))
            save(folder/'failure-integrity.json',verify(sid,user_ids[:min(len(user_ids), int(json.loads((folder/'k6-summary.json').read_text(encoding='utf-8-sig'))['metrics']['users_started']['values']['count']))]))
            save(folder/'failure-prometheus.json',{label:json.loads(get('http://127.0.0.1:9090/api/v1/query_range?'+urllib.parse.urlencode(dict(query=query,start=start,end=time.time(),step=5)))) for label,query in QUERIES.items()})
        except Exception as evidence_error:
            save(folder/'failure-evidence-error.json',dict(reason=str(evidence_error)))
        raise
    finally:
        # Recovery is recorded before cleanup; cleanup is not evidence of natural recovery.
        cleanup(sid)
        save(folder/'cleanup.json',dict(sessionId=sid,redisCleaned=True,dbFixturesRetained=True))
