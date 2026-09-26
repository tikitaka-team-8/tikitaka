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
import threading

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
parser.add_argument('--startup-spread-seconds', type=int, default=0,
                    help='Spread each VU first registration over this interval; later iterations are unchanged')
parser.add_argument('--mode', choices=['vu', 'spike', 'soak'], default='vu')
parser.add_argument('--target', choices=['gateway', 'ticketing'], default='gateway',
                    help='Route load through Gateway or call Ticketing directly with trusted test headers')
parser.add_argument('--registration-only', action='store_true',
                    help='Measure Queue registration only; skip admission journey and existing integrity/recovery checks')
parser.add_argument('--spike-vus', type=int, choices=[1000, 2000, 10000], default=1000,
                    help='Explicit direct-registration capacity probe; original S09 remains 1000')
parser.add_argument('--restart', choices=['none', 'gateway', 'apps'], default='none',
                    help='Optional controlled restart; infrastructure stays running')
parser.add_argument('--user-pool-size', type=int,
                    help='Override fixture users for a capacity comparison; must cover every started journey')
parser.add_argument('--label', default='', help='Safe suffix for the result directory')
parser.add_argument('--cleanup-db-fixtures', action='store_true',
                    help='Delete this run\'s generated users/session/seat after evidence is saved')
args = parser.parse_args()
if args.spike_vus != 1000 and not (args.mode == 'spike' and args.target == 'ticketing' and args.registration_only):
    raise SystemExit('--spike-vus above 1000 requires direct registration-only spike')
if args.mode == 'spike':
    args.vus, args.seconds = [args.spike_vus], 120
elif args.mode == 'soak':
    args.vus, args.seconds = [300], 600
allowed_vus = [50, 100, 300, 500, 1000] + ([2000, 10000] if args.registration_only else [])
if any(v not in allowed_vus for v in args.vus) or (args.mode == 'vu' and args.seconds != 120):
    raise SystemExit('This runner is bounded to the planned VU levels and 120 second steps')
if args.user_pool_size is not None and args.user_pool_size <= 0:
    raise SystemExit('--user-pool-size must be positive')
if args.mode == 'spike' and args.user_pool_size is not None and args.user_pool_size < args.spike_vus:
    raise SystemExit('--user-pool-size must cover every Spike VU')
if args.startup_spread_seconds < 0:
    raise SystemExit('--startup-spread-seconds must be zero or positive')
if args.target == 'ticketing' and args.restart != 'none':
    raise SystemExit('Direct Ticketing comparison requires --restart none')
if args.registration_only and (args.target != 'ticketing' or args.mode != 'spike'):
    raise SystemExit('--registration-only is limited to --target ticketing --mode spike')
if args.cleanup_db_fixtures and len(args.vus) != 1:
    raise SystemExit('--cleanup-db-fixtures requires exactly one --vus value')
if args.label and not re.fullmatch(r'[A-Za-z0-9._-]+', args.label):
    raise SystemExit('--label may contain only letters, numbers, dot, underscore and hyphen')
suffix = '-s09-' + args.mode + (('-direct' if args.target == 'ticketing' else '')) + (('-register' if args.registration_only else '')) + (('-' + args.label) if args.label else '')
OUT = ROOT / 'artifacts/queue-load' / (dt.datetime.now().strftime('%Y%m%d-%H%M%S') + suffix)
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


def capture_observation(folder, component, action, default=None):
    """Observation failure must not cancel measured requests or look like a zero metric."""
    try:
        return action()
    except Exception as error:
        path = folder / 'collection-errors.json'
        errors = json.loads(path.read_text(encoding='utf-8')) if path.exists() else []
        errors.append(dict(at=time.time(), component=component,
                           errorType=type(error).__name__, message=str(error)))
        save(path, errors)
        print('Observation unavailable:', component, type(error).__name__, flush=True)
        return default


def get(url):
    with urllib.request.urlopen(url, timeout=8) as r:
        return r.read().decode()


def metrics(port):
    raw = get(f'http://127.0.0.1:{port}/actuator/prometheus')
    return {line.rsplit(' ', 1)[0]: float(line.rsplit(' ', 1)[1]) for line in raw.splitlines()
            if line and not line.startswith('#') and not line.endswith('NaN')}


def tcp_snapshot():
    result = {'at': time.time(), 'containers': {}}
    for name in ['tikitaka-ticketing-service']:
        identity = json.loads(cmd(['docker', 'inspect', name]))[0]
        lines = cmd(['docker', 'exec', name, 'cat', '/proc/net/netstat']).splitlines()
        counters = {}
        for i in range(0, len(lines)-1, 2):
            if lines[i].startswith('TcpExt:'):
                values = dict(zip(lines[i].split()[1:], map(int, lines[i+1].split()[1:])))
                counters = {key: values[key] for key in [
                    'ListenOverflows', 'ListenDrops', 'TCPSynRetrans',
                    'TCPTimeouts', 'TCPReqQFullDoCookies', 'TCPReqQFullDrop'] if key in values}
        result['containers'][name] = dict(id=identity['Id'], startedAt=identity['State']['StartedAt'], counters=counters)
    return result






def collect_tomcat(stop, path):
    samples = []
    while not stop.is_set():
        sample = {'at': time.time()}
        try:
            m = metrics(8082)
            sample['metrics'] = {k: v for k, v in m.items() if k.startswith('tomcat_')}
            sample['available'] = bool(sample['metrics'])
        except Exception as error:
            sample['error'] = str(error)
        samples.append(sample)
        save(path, samples)
        stop.wait(1)


def snapshot(sid, k6name=None):
    targets = ['tikitaka-ticketing-service', 'tikitaka-ticketing-redis']
    if args.target == 'gateway':
        targets.extend(['tikitaka-gateway', 'tikitaka-platform-service'])
    if k6name:
        targets.append(k6name)
    p = subprocess.run(['docker', 'stats', '--no-stream', '--format', '{{json .}}', *targets],
                       capture_output=True, text=True, encoding='utf-8', timeout=20)
    stats = [json.loads(line) for line in p.stdout.splitlines() if line.startswith('{')]
    m = metrics(8082)
    platform = metrics(8081) if args.target == 'gateway' else {}
    gateway = metrics(8000) if args.target == 'gateway' else {}
    info = redis('INFO', 'stats') + '\n' + redis('INFO', 'memory') + '\n' + redis('INFO', 'commandstats')
    with socket.create_connection(('127.0.0.1', 6381), timeout=3) as connection:
        t = time.perf_counter()
        connection.sendall(b'*1\r\n$4\r\nPING\r\n')
        if connection.recv(64) != b'+PONG\r\n':
            raise RuntimeError('Redis PING failed')
        ping_ms = (time.perf_counter() - t) * 1000
    return dict(at=time.time(), waiting=int(redis('ZCARD', 'queue:waiting:{' + sid + '}')),
                active=int(redis('ZCARD', 'queue:active:{' + sid + '}')),
                ticketingCpu=m.get('process_cpu_usage'), gatewayCpu=gateway.get('process_cpu_usage'),
                platformCpu=platform.get('process_cpu_usage'),
                heapBytes=sum(v for k,v in m.items() if k.startswith('jvm_memory_used_bytes{') and 'area="heap"' in k),
                platformHeapBytes=sum(v for k,v in platform.items() if k.startswith('jvm_memory_used_bytes{') and 'area="heap"' in k),
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
 'up': 'up{job=~"gateway|ticketing-service|platform-service"}',
 'requests': 'sum by(job,uri,method,status)(rate(http_server_requests_seconds_count{job=~"gateway|ticketing-service|platform-service",uri!~"/actuator.*"}[1m]))',
 'waiting': 'queue_waiting_count{job="ticketing-service"}',
 'admission': 'rate(queue_admission_total{job="ticketing-service"}[1m])',
 'scheduler': 'rate(queue_scheduler_duration_seconds_sum{job="ticketing-service"}[1m])/rate(queue_scheduler_duration_seconds_count{job="ticketing-service"}[1m])',
 'cpu': 'process_cpu_usage{job=~"gateway|ticketing-service|platform-service"}',
 'heap': 'sum by(job)(jvm_memory_used_bytes{job=~"gateway|ticketing-service|platform-service",area="heap"})',
 'gcPause': 'sum by(job)(rate(jvm_gc_pause_seconds_sum{job=~"gateway|ticketing-service|platform-service"}[1m]))',
 'gcCount': 'sum by(job)(rate(jvm_gc_pause_seconds_count{job=~"gateway|ticketing-service|platform-service"}[1m]))',
 'dbPool': 'hikaricp_connections{job="ticketing-service"}',
 'tomcatThreads': 'tomcat_threads_busy_threads{job="ticketing-service"} or tomcat_threads_current_threads{job="ticketing-service"} or tomcat_threads_config_max_threads{job="ticketing-service"}',
 'tomcatConnections': 'tomcat_connections_current_connections{job="ticketing-service"} or tomcat_connections_max_connections{job="ticketing-service"}'
}


print('Results:', OUT, flush=True)
# Parse with the actual k6 runtime before touching application fixtures or starting load.
with tempfile.TemporaryDirectory(prefix='script-check-', dir=OUT) as script_check:
    save(Path(script_check)/'users.json', [{'userId': 1, 'accessToken': ''}])
    checked = subprocess.run([
        'docker', 'run', '--rm', '--network', 'none',
        '--mount', f'type=bind,source={SCENARIO},target=/scripts,readonly',
        '--mount', f'type=bind,source={script_check},target=/private,readonly',
        '-e', 'SESSION_ID=script-check', '-e', 'SEAT_ID=script-check',
        '-e', 'VUS='+str(max(args.vus)), '-e', 'MODE='+args.mode, '-e', 'TARGET='+args.target,
        '-e', 'FLOW='+('register' if args.registration_only else 'journey'),
        K6, 'inspect', '--include-system-env-vars', '/scripts/queue-vu.js'],
        capture_output=True, text=True, encoding='utf-8', timeout=60)
    (OUT/'script-check.log').write_text(checked.stdout+'\n'+checked.stderr, encoding='utf-8')
    if checked.returncode:
        raise RuntimeError('k6 script initialization failed before load; see script-check.log')
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
        health_ports = [8081, 8082] if args.target == 'ticketing' else [8000, 8081, 8082]
        for port in health_ports:
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
container_names = (['tikitaka-gateway'] if args.target == 'gateway' else []) + [
    'tikitaka-ticketing-service', 'tikitaka-platform-service']
containers = json.loads(cmd(['docker', 'inspect', *container_names]))
def env(c):
    return dict(v.split('=',1) for v in c['Config']['Env'])
container_by_name = {c['Name'].lstrip('/'): c for c in containers}
ticketing_container = container_by_name['tikitaka-ticketing-service']
platform_container = container_by_name['tikitaka-platform-service']
ticketing_environment = env(ticketing_container)
platform_route = ticketing_environment.get('CLIENTS_PLATFORM_SERVICE_URL', 'http://platform-service:8081')
if args.target == 'ticketing' and platform_route != 'http://platform-service:8081':
    raise RuntimeError('Direct registration requires the real Platform service; restore its route before running')
platform_environment = env(platform_container)
if ticketing_environment.get('INTERNAL_SERVICE_KEY') != platform_environment.get('INTERNAL_SERVICE_KEY'):
    raise RuntimeError('Ticketing/Platform INTERNAL_SERVICE_KEY mismatch; no fixtures or load started')
if args.target == 'ticketing':
    for setting in ['QUEUE_ADMISSIONS_PER_SECOND', 'QUEUE_ADMISSION_BATCH_SIZE']:
        configured = ticketing_environment.get(setting)
        if configured is not None and configured != '50':
            raise RuntimeError(f'Direct comparison requires {setting}=50, found {configured}')
if args.target == 'gateway':
    gateway_container = container_by_name['tikitaka-gateway']
    if env(gateway_container)['JWT_SECRET'] != platform_environment['JWT_SECRET']:
        raise RuntimeError('Gateway/Platform JWT configuration mismatch')
    secret_key = base64.b64decode(env(gateway_container)['JWT_SECRET'])
else:
    secret_key = None
save(OUT/'environment.json', {'branch':cmd(['git','branch','--show-current']), 'commit':cmd(['git','rev-parse','HEAD']),
 'mode':args.mode,'target':args.target,'registrationOnly':args.registration_only,
 'authentication':('Local fixture JWT through real Gateway' if args.target == 'gateway'
                   else 'Trusted X-User-Id/X-User-Role test headers; Gateway intentionally bypassed'),
 'images':[{'name':c['Name'],'image':c['Image'],'labels':c['Config'].get('Labels'),'restartCount':c['RestartCount'], 'startedAt':c['State']['StartedAt']} for c in containers],
 'restart':args.restart,'healthReadyAt':startup['readyAt'],
 'vus':args.vus,'seconds':args.seconds,'pollSeconds':args.poll_seconds,
 'startupSpreadSeconds':args.startup_spread_seconds,'heartbeatSeconds':15,'thinkSeconds':0.2,'k6':K6,
 'queueAdmissionsPerSecond':ticketing_environment.get('QUEUE_ADMISSIONS_PER_SECOND','application-default'),
 'tomcatEnvironment':{key:ticketing_environment.get(key,'application-default') for key in [
     'SERVER_TOMCAT_ACCEPT_COUNT','SERVER_TOMCAT_THREADS_MAX',
     'SERVER_TOMCAT_THREADS_MIN_SPARE','SERVER_TOMCAT_MAX_CONNECTIONS']},
 'queueAdmissionBatchSize':ticketing_environment.get('QUEUE_ADMISSION_BATCH_SIZE','application-default')})
for source in [Path(__file__), SCENARIO/'queue-vu.js']:
    (OUT/('executed-'+source.name)).write_bytes(source.read_bytes())
prefix = 's09vu-' + uuid.uuid4().hex[:12]
user_base = int(time.time()*1000)*100
default_pool_size = 40000 if args.mode == 'soak' else (args.spike_vus if args.mode == 'spike' else 12000)
pool_size = args.user_pool_size or default_pool_size
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
        # Verify the new offline fixture auth path once before any load; no fake user headers.
        fixture_token = token(user_ids[0], secret_key) if secret_key else None
        queue_port = 8082 if args.target == 'ticketing' else 8000
        queue_url = f'http://127.0.0.1:{queue_port}/api/v1/event-sessions/{sid}/queue'
        identity_headers = ({'Authorization':'Bearer '+fixture_token} if fixture_token else
                            {'X-User-Id':str(user_ids[0]), 'X-User-Role':'USER'})
        req = urllib.request.Request(queue_url+'/me', headers=identity_headers)
        try:
            urllib.request.urlopen(req,timeout=5)
            raise RuntimeError('New fixture unexpectedly exists')
        except urllib.error.HTTPError as e:
            if e.code != 404 or json.loads(e.read()).get('code') != 'Q-002':
                raise RuntimeError('Fixture JWT authentication failed')
        # Exercise Gateway -> Ticketing -> Platform before starting k6. This catches
        # service-key and internal sales-status failures without wasting a load run.
        register_req = urllib.request.Request(
            queue_url, data=b'', method='POST', headers=identity_headers)
        try:
            with urllib.request.urlopen(register_req, timeout=8) as response:
                register_body = json.load(response)
                register_status = response.status
        except urllib.error.HTTPError as error:
            error_body = error.read().decode('utf-8', errors='replace')[:1000]
            save(folder/'preflight.json', dict(valid=False, httpStatus=error.code, body=error_body))
            raise RuntimeError('Queue dependency preflight failed; see preflight.json')
        preflight_valid = register_status == 200 and register_body.get('code') == 'SUCCESS'
        save(folder/'preflight.json', dict(valid=preflight_valid, httpStatus=register_status,
                                            code=register_body.get('code')))
        if not preflight_valid:
            raise RuntimeError('Queue dependency preflight returned an unexpected response')
        cleanup(sid)
        before = snapshot(sid)
        save(folder/'baseline.json', before)
        if args.registration_only:
            tcp_before = tcp_snapshot()
            save(folder/'tcp-before.json', tcp_before)
        with tempfile.TemporaryDirectory(prefix='private-',dir=folder) as private:
            save(Path(private)/'users.json',[{'userId':i,'accessToken':token(i,secret_key) if secret_key else ''} for i in user_ids])
            name = 's09-vu-'+str(vus)+'-'+sid[:8]
            command = ['docker','run','--rm','--name',name,'--network','tikitaka-network',
                '--mount',f'type=bind,source={SCENARIO},target=/scripts,readonly',
                '--mount',f'type=bind,source={private},target=/private,readonly',
                '--mount',f'type=bind,source={folder},target=/results',
                '-e','SESSION_ID='+sid,'-e','SEAT_ID='+seat,'-e','VUS='+str(vus),'-e','SECONDS='+str(args.seconds),'-e','MODE='+args.mode,'-e','POLL_SECONDS='+str(args.poll_seconds),
                '-e','TARGET='+args.target,
                '-e','FLOW='+('register' if args.registration_only else 'journey'),
                '-e','STARTUP_SPREAD_SECONDS='+str(args.startup_spread_seconds),
                '-e','K6_WEB_DASHBOARD=true','-e','K6_WEB_DASHBOARD_PERIOD=2s','-e','K6_WEB_DASHBOARD_PORT=-1',
                '-e','K6_WEB_DASHBOARD_EXPORT=/results/report.html',K6,'run','--quiet','--out','json=/results/metrics.json','/scripts/queue-vu.js']
            start = time.time()
            save(folder/'timing.json',dict(launch=start,healthReadyAt=startup['readyAt'],readyToLoadSeconds=start-startup['readyAt']))
            print('START VUs',vus,flush=True)
            with (folder/'k6.log').open('w',encoding='utf-8') as log:
                monitor_stop = threading.Event()
                monitor = None
                if args.registration_only:
                    monitor = threading.Thread(target=collect_tomcat,
                        args=(monitor_stop, folder/'tomcat-samples.json'), daemon=True)
                    monitor.start()
                process = subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT)
                samples=[]
                try:
                    while process.poll() is None:
                        time.sleep(5)
                        sample=capture_observation(folder, 'periodic-snapshot',
                            lambda: snapshot(sid,name if process.poll() is None else None))
                        if sample is None:
                            continue
                        samples.append(sample)
                        save(folder/'samples.json',samples)
                        print('VU',vus,'elapsed',round(time.time()-start),'waiting',sample['waiting'],'active',sample['active'],flush=True)
                except Exception:
                    cmd(['docker','stop','--time','5',name])
                    process.wait(timeout=15)
                    raise
                finally:
                    monitor_stop.set()
                    if monitor:
                        monitor.join(timeout=10)
            end=time.time()
            if args.registration_only:
                def collect_registration_evidence():
                    tcp_after = tcp_snapshot()
                    save(folder/'tcp-after.json', tcp_after)
                    deltas = {}
                    for name, initial in tcp_before['containers'].items():
                        final = tcp_after['containers'][name]
                        valid = initial['id'] == final['id'] and initial['startedAt'] == final['startedAt']
                        delta = {k: final['counters'][k]-v for k,v in initial['counters'].items()}
                        valid = valid and all(v >= 0 for v in delta.values())
                        deltas[name] = dict(valid=valid, delta=delta if valid else None)
                    save(folder/'tcp-delta.json', deltas)
                capture_observation(folder, 'registration-final-evidence', collect_registration_evidence)
            save(folder/'timing.json',dict(launch=start,processEnd=end,healthReadyAt=startup['readyAt'],readyToLoadSeconds=start-startup['readyAt']))
            if process.returncode != 0 and not args.registration_only:
                raise RuntimeError('k6 checks failed; see saved k6.log; higher VU steps cancelled')
        summary=json.loads((folder/'k6-summary.json').read_text(encoding='utf-8-sig'))['metrics']
        started=int(summary['users_started']['values']['count'])
        completion_metric = 'queue_registration_completed' if args.registration_only else 'normal_journey_completed'
        completed=int(summary[completion_metric]['values']['count'])
        if args.mode != 'vu' and not args.registration_only:
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
        if not args.registration_only:
            integrity=verify(sid,user_ids[:started])
            save(folder/'integrity.json',integrity)
            if not integrity['valid'] or started != completed:
                raise RuntimeError('Incomplete journey or Redis integrity failure; higher steps cancelled')
        recovery=[]
        if not args.registration_only:
            # Operational gate, not team latency/TPS SLO. Record raw heap rather than require GC at an exact moment.
            cpu_limit=max(0.10,(before['ticketingCpu'] or 0)+0.05)
            gateway_limit=(max(0.10,(before['gatewayCpu'] or 0)+0.05) if args.target == 'gateway' else None)
            stable=0
            for _ in range(18):
                sample=snapshot(sid)
                recovery.append(sample)
                gateway_stable = args.target != 'gateway' or sample['gatewayCpu'] <= gateway_limit
                stable = stable+1 if sample['waiting']==0 and sample['active']==0 and sample['ticketingCpu']<=cpu_limit and gateway_stable else 0
                save(folder/'recovery.json',dict(samples=recovery,stable=stable,cpuLimit=cpu_limit,gatewayCpuLimit=gateway_limit))
                if stable>=3:
                    break
                time.sleep(5)
            if stable<3:
                raise RuntimeError('Queue/CPU recovery gate not met; higher steps cancelled')
        after=json.loads(cmd(['docker', 'inspect', *container_names]))
        if any(c['RestartCount']!=old['RestartCount'] or c['State'].get('OOMKilled') or not c['State']['Running'] for c,old in zip(after,containers)):
            raise RuntimeError('Restart/OOM/stopped service detected; higher steps cancelled')
        ranges={}
        for label,query in QUERIES.items():
            ranges[label]=capture_observation(folder, 'prometheus-range:'+label,
                lambda: json.loads(get('http://127.0.0.1:9090/api/v1/query_range?'+urllib.parse.urlencode(dict(query=query,start=start,end=time.time(),step=5)))))
        save(folder/'prometheus.json',ranges)
        result=dict(vus=vus,started=started,completed=completed,requests=summary['http_reqs']['values']['count'],
                    failed=summary['http_req_failed']['values']['rate'],
                    wait=(None if args.registration_only else summary['admission_observed_wait_ms']['values']),
                    http=summary['http_req_duration']['values'],
                    target=args.target,
                    registrationFailed=(int(summary.get('queue_registration_failed',{}).get('values',{}).get('count',0)) if args.registration_only else None),
                    registrationElapsed=summary.get('queue_registration_elapsed_ms',{}).get('values',{}),
                    k6ExitCode=process.returncode,
                    queueRegisterHttp=summary.get('http_req_duration{name:queue_register}',{}).get('values',{}),
                    queueRegisterConnecting=summary.get('http_req_connecting{name:queue_register}',{}).get('values',{}),
                    queueRegisterWaiting=summary.get('http_req_waiting{name:queue_register}',{}).get('values',{}),
                    seatListHttp=summary.get('http_req_duration{name:seat_list}',{}).get('values',{}),
                    queueAdmissionsPerSecond=ticketing_environment.get('QUEUE_ADMISSIONS_PER_SECOND','application-default'),
                    queueAdmissionBatchSize=ticketing_environment.get('QUEUE_ADMISSION_BATCH_SIZE','application-default'),
                    startupSpreadSeconds=args.startup_spread_seconds,
                    waitingSampleMax=max((s['waiting'] for s in samples), default=None),
                    observationComplete=not (folder/'collection-errors.json').exists(),
                    postProcessRecoverySeconds=(recovery[-1]['at']-end if recovery else None))
        save(folder/'result.json',result)
        registration_passed = (not args.registration_only or (
            started == vus and completed == vus and result['requests'] == vus and result['registrationFailed'] == 0))
        print('PASS' if process.returncode == 0 and registration_passed else 'FAIL',json.dumps(result),flush=True)
        if process.returncode != 0 or not registration_passed:
            raise RuntimeError('Load failed; full result.json and diagnostics saved')
    except Exception as error:
        save(folder/'stopped.json',dict(reason=str(error),at=time.time()))
        # Preserve failure evidence before fixture cleanup, including unfinished entries.
        try:
            save(folder/'failure-snapshot.json',snapshot(sid))
            if not args.registration_only and (folder/'k6-summary.json').exists():
                save(folder/'failure-integrity.json',verify(sid,user_ids[:min(len(user_ids), int(json.loads((folder/'k6-summary.json').read_text(encoding='utf-8-sig'))['metrics']['users_started']['values']['count']))]))
            save(folder/'failure-prometheus.json',{label:json.loads(get('http://127.0.0.1:9090/api/v1/query_range?'+urllib.parse.urlencode(dict(query=query,start=start,end=time.time(),step=5)))) for label,query in QUERIES.items()})
        except Exception as evidence_error:
            save(folder/'failure-evidence-error.json',dict(reason=str(evidence_error)))
        raise
    finally:
        # Recovery is recorded before cleanup; cleanup is not evidence of natural recovery.
        cleanup(sid)
        database_cleaned = False
        if args.cleanup_db_fixtures:
            sql('tikitaka-ticketing-postgres',
                f"DELETE FROM p_schedule_seat WHERE schedule_seat_id='{seat}';")
            sql('tikitaka-platform-postgres', f"""
                DELETE FROM p_event_session WHERE id='{sid}';
                DELETE FROM p_user
                WHERE id BETWEEN {user_base + 1} AND {user_base + pool_size}
                  AND email LIKE '{prefix}-%@test.tikitaka.local';
            """)
            database_cleaned = True
        save(folder/'cleanup.json',dict(sessionId=sid,redisCleaned=True,
                                        dbFixturesRetained=not database_cleaned,
                                        databaseCleaned=database_cleaned))
