"""Local S09 Gateway preflight: functional checks and five authenticated k6 journeys.
Requires the existing local Compose services and template session. No production targets.
Credentials remain in memory / a temporary mount removed on exit. No token bodies are logged.
"""
import concurrent.futures as futures
import datetime as dt
import json
from pathlib import Path
import secrets
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[3]
SCENARIO = Path(__file__).resolve().parent
BASE = 'http://127.0.0.1:8000/api/v1'
K6 = 'grafana/k6@sha256:5221b620a4f874faff6e32ba597aa667c058391fe4898b1c6f6377f062c6cdec'
RUN = dt.datetime.now().strftime('%Y%m%d-%H%M%S') + '-s09'
OUT = ROOT / 'artifacts/queue-load' / RUN
OUT.mkdir(parents=True)
checks, sessions, accounts = [], [], []


def command(args, data=None):
    p = subprocess.run(args, input=data, text=True, encoding='utf-8', capture_output=True)
    if p.returncode:
        raise RuntimeError('Local command failed: ' + args[0])
    return p.stdout.strip()


def redis(*args):
    return command(['docker', 'exec', 'tikitaka-ticketing-redis', 'redis-cli', '--raw', *map(str, args)])


def sql(container, statement):
    return command(['docker', 'exec', '-i', container, 'sh', '-c',
                    'psql -X -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], statement)


def request(method, path, account=None, body=None, extra=None):
    headers = {'Content-Type': 'application/json'}
    if account:
        headers['Authorization'] = 'Bearer ' + account['accessToken']
    headers.update(extra or {})
    req = urllib.request.Request(BASE + path, method=method, headers=headers,
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        data = response.read()
        try:
            parsed = json.loads(data) if data else {}
        except ValueError:
            parsed = {}
        return response.status, parsed


def record(name, condition, **evidence):
    checks.append(dict(case=name, passed=bool(condition), at=dt.datetime.now(dt.timezone.utc).isoformat(), **evidence))
    (OUT / 'checks.json').write_text(json.dumps(checks, indent=2), encoding='utf-8')
    print(('PASS ' if condition else 'FAIL ') + name, flush=True)
    if not condition:
        raise AssertionError(name)


def expect(name, result, status, code=None):
    actual, body = result
    record(name, actual == status and (code is None or body.get('code') == code),
           expectedStatus=status, actualStatus=actual, expectedCode=code, actualCode=body.get('code'))
    return body.get('data', {})


def queue(sid):
    return '/event-sessions/' + sid + '/queue'


def seats(sid):
    return '/schedules/' + sid + '/seats'


def fixture():
    sid, seat = str(uuid.uuid4()), str(uuid.uuid4())
    statement = f"""INSERT INTO p_event_session
    (id,event_id,session_number,performance_start_at,performance_end_at,sales_open_at,sales_close_at,status,queue_enabled)
    SELECT '{sid}',s.event_id,(SELECT COALESCE(MAX(session_number),0)+1 FROM p_event_session WHERE event_id=s.event_id),
    now()+interval '2 days',now()+interval '2 days 2 hours',now()-interval '1 hour',now()+interval '1 day','SCHEDULED',true
    FROM p_event_session s WHERE s.id='31000000-0000-0000-0000-000000000001' RETURNING id;"""
    if sid not in sql('tikitaka-platform-postgres', statement):
        raise RuntimeError('Missing template session')
    sessions.append(sid)
    sql('tikitaka-ticketing-postgres', f"""INSERT INTO p_schedule_seat
    (schedule_seat_id,event_session_id,venue_seat_id,section,row_label,seat_number,seat_grade,price,seat_status,created_by,updated_by)
    VALUES ('{seat}','{sid}','{uuid.uuid4()}','S09','A','1','VIP',10000,'AVAILABLE',1,1);""")
    return sid, seat


def admitted(sid, account):
    for _ in range(90):
        status, body = request('GET', queue(sid) + '/me', account)
        if status != 200:
            raise AssertionError('Admission polling failed')
        state = body['data']
        if state['status'] == 'ADMITTED':
            return state
        time.sleep(0.5)
    raise AssertionError('Admission not observed within diagnostic deadline')


def concurrent_requests(count, action):
    barrier = threading.Barrier(count)
    def run(_):
        barrier.wait(timeout=10)
        return action()
    with futures.ThreadPoolExecutor(max_workers=count) as pool:
        return list(pool.map(run, range(count)))


def cleanup(sid):
    # Only fresh UUID session keys; never FLUSHDB or unrelated sessions.
    keys = redis('--scan', '--pattern', 'queue:*{' + str(uuid.UUID(sid)) + '}*').splitlines()
    for offset in range(0, len(keys), 100):
        redis('DEL', *keys[offset:offset + 100])
    redis('SREM', 'queue:waiting-sessions', sid)
    redis('SREM', 'queue:active-sessions', sid)


def execute():
    images = json.loads(command(['docker', 'inspect', 'tikitaka-gateway', 'tikitaka-ticketing-service', 'tikitaka-platform-service']))
    metadata = {'runId': RUN, 'branch': command(['git', 'branch', '--show-current']),
                'commit': command(['git', 'rev-parse', 'HEAD']), 'purpose': 'S09 steps 1-2, not load capacity',
                'images': [{'name': c['Name'], 'id': c['Image'], 'labels': c['Config'].get('Labels')} for c in images]}
    (OUT / 'run.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    for i in range(8):
        email = 's09.' + uuid.uuid4().hex + '@test.tikitaka.local'
        password = 'S09!Aa1-' + secrets.token_hex(8)
        user = expect('signup-' + str(i), request('POST', '/auth/signup', body={
            'email': email, 'password': password, 'name': 'S09 tester', 'nickname': 's09-' + secrets.token_hex(4)}), 201)
        login = expect('login-' + str(i), request('POST', '/auth/login', body={'email': email, 'password': password}), 200)
        accounts.append({'userId': user['userId'], 'accessToken': login['accessToken']})

    flow_sid, flow_seat = fixture()
    exp_sid, _ = fixture()
    dup_sid, _ = fixture()
    wait_sid, _ = fixture()
    (OUT / 'fixtures.json').write_text(json.dumps({'sessions': sessions, 'users': [a['userId'] for a in accounts],
        'flowSeatId': flow_seat, 'syntheticWaitingEntries': 2000}, indent=2), encoding='utf-8')
    a, b, c = accounts[5:]
    expect('missing-jwt', request('GET', queue(flow_sid) + '/me'), 401)
    expect('invalid-jwt', request('GET', queue(flow_sid) + '/me', extra={'Authorization': 'Bearer invalid'}), 401)
    expect('no-entry-cannot-bypass', request('GET', seats(flow_sid), a,
                                           extra={'X-Queue-Token': str(uuid.uuid4())}), 403, 'Q-001')
    expect('internal-route-blocked', request('GET', '/internal/event-sessions/' + flow_sid + '/sales-status', a,
                                           extra={'X-Service-Key': 'forged'}), 403)

    expect('expiry-user-register', request('POST', queue(exp_sid), a), 200)
    expiry = admitted(exp_sid, a)
    expect('expiry-other-user-register', request('POST', queue(exp_sid), b), 200)
    admitted(exp_sid, b)
    expect('other-users-token-rejected', request('GET', seats(exp_sid), b,
                                                extra={'X-Queue-Token': expiry['admissionToken']}), 403, 'Q-001')
    expect('fabricated-token-rejected', request('GET', seats(exp_sid), a,
                                               extra={'X-Queue-Token': str(uuid.uuid4())}), 403, 'Q-001')
    expect('missing-queue-token-rejected', request('GET', seats(exp_sid), a), 400, 'C-002')
    expect('cross-session-register', request('POST', queue(dup_sid), a), 200)
    admitted(dup_sid, a)
    expect('other-session-token-rejected', request('GET', seats(dup_sid), a,
                                                 extra={'X-Queue-Token': expiry['admissionToken']}), 403, 'Q-001')

    responses = concurrent_requests(20, lambda: request('POST', queue(dup_sid), b,
                                   extra={'X-User-Id': str(a['userId']), 'X-User-Role': 'ADMIN'}))
    record('20-concurrent-duplicate-register-and-header-sanitization',
           all(status == 200 and body.get('data', {}).get('userId') == b['userId'] for status, body in responses), requests=20)
    state = admitted(dup_sid, b)
    sequence = int(redis('GET', 'queue:sequence:{' + dup_sid + '}'))
    record('duplicate-register-no-new-sequence', sequence == 2, actualSequence=sequence, expectedUsers=2)
    responses = concurrent_requests(10, lambda: request('GET', seats(dup_sid), b,
                                    extra={'X-Queue-Token': state['admissionToken']}))
    record('10-concurrent-first-entry-idempotent', all(status == 200 for status, _ in responses), requests=10)
    final = expect('entered-query', request('GET', queue(dup_sid) + '/me', b), 200)
    record('single-entered-state', final['status'] == 'ENTERED', status=final['status'])
    token_keys = redis('--scan', '--pattern', 'queue:admission-token:{' + dup_sid + '}:*').splitlines()
    record('duplicate-admission-no-extra-token-keys', len(token_keys) == 2, actualTokenKeys=len(token_keys), expectedUsers=2)
    expect('used-token-same-owner-retry', request('GET', seats(dup_sid), b,
                                               extra={'X-Queue-Token': state['admissionToken']}), 200)
    expect('heartbeat-after-entered', request('POST', queue(dup_sid) + '/me/heartbeat', b), 409, 'Q-007')
    expect('leave-after-entered', request('DELETE', queue(dup_sid) + '/me', b), 409, 'Q-003')

    # A bounded synthetic backlog keeps tested users WAITING without changing scheduler policy.
    now = dt.datetime.now(dt.timezone.utc)
    tag = '{' + wait_sid + '}'
    seed = """for i=1,2000 do local uid=tostring(9000000000000+i)
      local key='queue:entry:'..ARGV[1]..':'..uid
      redis.call('HSET',key,'status','WAITING','sequence',i,'joinedAt',ARGV[2],'expiresAt',ARGV[3])
      redis.call('EXPIRE',key,3600)
      redis.call('ZADD','queue:waiting:'..ARGV[1],i,uid)
      redis.call('ZADD','queue:waiting-heartbeat:'..ARGV[1],ARGV[4],uid) end
      redis.call('SET','queue:sequence:'..ARGV[1],2000,'EX',3600)
      redis.call('EXPIRE','queue:waiting:'..ARGV[1],3600)
      redis.call('EXPIRE','queue:waiting-heartbeat:'..ARGV[1],3600)
      return 2000"""
    redis('EVAL', seed, 0, tag, now.isoformat(), (now + dt.timedelta(hours=1)).isoformat(), int(now.timestamp()*1000))
    first = expect('waiting-first-register', request('POST', queue(wait_sid), b), 200)
    second = expect('waiting-second-register', request('POST', queue(wait_sid), c), 200)
    record('backlog-users-remain-waiting', first['status'] == second['status'] == 'WAITING')
    expect('waiting-cannot-bypass', request('GET', seats(wait_sid), b,
                                          extra={'X-Queue-Token': state['admissionToken']}), 403, 'Q-001')
    expect('waiting-heartbeat', request('POST', queue(wait_sid) + '/me/heartbeat', b), 204)
    seq_b = int(redis('HGET', 'queue:entry:' + tag + ':' + str(b['userId']), 'sequence'))
    seq_c = int(redis('HGET', 'queue:entry:' + tag + ':' + str(c['userId']), 'sequence'))
    record('sequential-join-order', (seq_b, seq_c) == (2001, 2002), sequences=[seq_b, seq_c])
    expect('waiting-explicit-leave', request('DELETE', queue(wait_sid) + '/me', b), 200)
    expect('left-user-no-entry', request('GET', queue(wait_sid) + '/me', b), 404, 'Q-002')
    expect('leave-idempotent', request('DELETE', queue(wait_sid) + '/me', b), 200)
    expect('rejoin-after-leave', request('POST', queue(wait_sid), b), 200)
    new_seq = int(redis('HGET', 'queue:entry:' + tag + ':' + str(b['userId']), 'sequence'))
    rank_b = int(redis('ZRANK', 'queue:waiting:' + tag, b['userId']))
    rank_c = int(redis('ZRANK', 'queue:waiting:' + tag, c['userId']))
    record('rejoin-goes-behind-existing-user', new_seq == 2003 and rank_b > rank_c, sequence=new_seq, relativeRankGap=rank_b-rank_c)
    cleanup(wait_sid)

    with tempfile.TemporaryDirectory(prefix='private-', dir=OUT) as private:
        Path(private, 'users.json').write_text(json.dumps(accounts[:5]), encoding='utf-8')
        args = ['docker', 'run', '--rm', '--network', 'tikitaka-network',
                '--mount', f'type=bind,source={Path(__file__).parent},target=/scripts,readonly',
                '--mount', f'type=bind,source={private},target=/private,readonly',
                '--mount', f'type=bind,source={OUT},target=/results',
                '-e', 'SESSION_ID=' + flow_sid, '-e', 'SEAT_ID=' + flow_seat,
                K6, 'run', '--quiet', '/scripts/queue-load.js']
        run = subprocess.run(args)
        record('five-authenticated-k6-journeys', run.returncode == 0, users=5)

    audit = """local seen={} local entered=0 local refs=0 local tokens=0 local seqs={}
      for i=2,#ARGV do local uid=ARGV[i]
       local e='queue:entry:'..ARGV[1]..':'..uid
       if redis.call('HGET',e,'status')=='ENTERED' then entered=entered+1 end
       local seq=redis.call('HGET',e,'sequence'); if seq then seqs[seq]=true end
       local token=redis.call('GET','queue:admission-token-ref:'..ARGV[1]..':'..uid)
       if token then refs=refs+1; seen[token]=true
        if redis.call('HGET','queue:admission-token:'..ARGV[1]..':'..token,'status')=='USED' then tokens=tokens+1 end end end
      local unique=0;for _ in pairs(seen) do unique=unique+1 end
      local sequenceUnique=0;for _ in pairs(seqs) do sequenceUnique=sequenceUnique+1 end
      return cjson.encode({entered=entered,refs=refs,usedTokens=tokens,uniqueTokens=unique,uniqueSequences=sequenceUnique})"""
    evidence = json.loads(redis('EVAL', audit, 0, '{' + flow_sid + '}', *[a['userId'] for a in accounts[:5]]))
    record('five-user-redis-entry-token-integrity', all(v == 5 for v in evidence.values()), **evidence)
    token_keys = redis('--scan', '--pattern', 'queue:admission-token:{' + flow_sid + '}:*').splitlines()
    record('five-user-no-extra-token-keys', len(token_keys) == 5, actualTokenKeys=len(token_keys))
    expected_expiry = dt.datetime.fromisoformat(expiry['expiresAt'].replace('Z', '+00:00')).timestamp()
    print('Waiting for real admission token TTL; policy unchanged.', flush=True)
    while time.time() < expected_expiry + 2:
        remaining = expected_expiry + 2 - time.time()
        print('Token expiry remaining seconds:', round(remaining), flush=True)
        time.sleep(min(20, remaining))
    expect('naturally-expired-token-rejected', request('GET', seats(exp_sid), a,
                                                     extra={'X-Queue-Token': expiry['admissionToken']}), 403, 'Q-001')
    record('expired-token-never-entered', redis('HGET', 'queue:entry:{' + exp_sid + '}:' + str(a['userId']), 'status') != 'ENTERED')


print('Results:', OUT, flush=True)
try:
    execute()
finally:
    cleanup_results = []
    for sid in sessions:
        try:
            cleanup(sid)
            cleanup_results.append({'sessionId': sid, 'redisCleaned': True})
        except Exception:
            cleanup_results.append({'sessionId': sid, 'redisCleaned': False})
    (OUT / 'cleanup.json').write_text(json.dumps(cleanup_results, indent=2), encoding='utf-8')
    print('Redis cleanup recorded. Local fixture DB rows retained; credentials not saved.', flush=True)
