import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import crypto from 'k6/crypto';

const session = __ENV.SESSION_ID;
const users = new SharedArray('authenticated-users', () => JSON.parse(open('/private/users.json')));
const vus = Number(__ENV.VUS);
const seconds = Number(__ENV.SECONDS || 120);
const mode = __ENV.MODE || 'vu';
const flow = __ENV.FLOW || 'journey';
if (!['journey', 'register'].includes(flow)) throw new Error('FLOW must be journey or register');
const startedUsers = new Counter('users_started');
const flowErrors = new Counter('flow_errors');
let user;
let accessToken;
const seatId = __ENV.SEAT_ID;
if (!session || !seatId || !users.length) throw new Error('Missing test fixture');
const target = __ENV.TARGET || 'gateway';
if (!['gateway', 'ticketing'].includes(target)) throw new Error('TARGET must be gateway or ticketing');
const directTicketing = target === 'ticketing';
const base = (directTicketing
  ? (__ENV.TICKETING_URL || 'http://ticketing-service:8082')
  : (__ENV.GATEWAY_URL || 'http://tikitaka-gateway:8000')) + '/api/v1';
const queue = `${base}/event-sessions/${session}/queue`;
// 학습용 간격: 상태 조회 2초, heartbeat 15초. 실제 프런트 정책이 정해지면 맞춘다.
const pollSeconds = Number(__ENV.POLL_SECONDS || 2);
if (![1, 2, 5].includes(pollSeconds)) throw new Error('POLL_SECONDS must be 1, 2 or 5');
const startupSpreadSeconds = Number(__ENV.STARTUP_SPREAD_SECONDS || 0);
if (!Number.isFinite(startupSpreadSeconds) || startupSpreadSeconds < 0) {
  throw new Error('STARTUP_SPREAD_SECONDS must be zero or positive');
}
const heartbeatMs = 15000;
const deadlineMs = 240000;
const completed = new Counter('normal_journey_completed');
const registrationCompleted = new Counter('queue_registration_completed');
const registrationFailed = new Counter('queue_registration_failed');
const registrationElapsed = new Trend('queue_registration_elapsed_ms', true);
const heartbeatAccepted = new Counter('heartbeat_accepted');
const heartbeatRace = new Counter('heartbeat_admission_race');
const admissionWait = new Trend('admission_observed_wait_ms', true);
const journeyTime = new Trend('normal_journey_ms', true);

export const options = {
  scenarios: { normal: mode === 'spike'
    ? { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: '250s' }
    : { executor: 'constant-vus', vus, duration: `${seconds}s`, gracefulStop: '250s' } },
  thresholds: { checks: ['rate==1'], http_req_failed: ['rate==0'], flow_errors: ['count==0'],
    'http_reqs{name:queue_register}': [], 'http_reqs{name:queue_status}': [],
    'http_reqs{name:queue_heartbeat}': [], 'http_reqs{name:seat_list}': [],
    'http_req_duration{name:queue_register}': [], 'http_req_duration{name:queue_status}': [],
    'http_req_duration{name:seat_list}': [],
    'http_req_connecting{name:queue_register}': [],
    'http_req_waiting{name:queue_register}': [] },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function requestHeaders(extra = {}) {
  const identity = directTicketing
    ? { 'X-User-Id': String(user), 'X-User-Role': 'USER' }
    : { Authorization: `Bearer ${accessToken}` };
  return { ...identity, ...extra };
}
function params(name, extra = {}) {
  return { headers: requestHeaders(), timeout: '5s', redirects: 0,
    tags: { name }, ...extra };
}
function requireCheck(condition, name) {
  if (!check(condition, { [name]: (ok) => ok })) { flowErrors.add(1); exec.test.abort(name); }
}
function json(response) {
  try { return response.json(); } catch (_) { return null; }
}
function statusOf(response, name) {
  const body = json(response);
  if (response.status !== 200 || body?.code !== 'SUCCESS') {
    console.error(`${name} failed: status=${response.status} error=${response.error || ''} body=${String(response.body || '').slice(0, 500)}`);
  }
  requireCheck(response.status === 200 && body?.code === 'SUCCESS', `${name}: HTTP 200 SUCCESS`);
  requireCheck(body.data?.userId === user && body.data?.sessionId === session, `${name}: correct owner`);
  return body.data;
}

export default function () {
  // Spread only each VU's first registration. Later iterations remain closed-model traffic.
  // This isolates the admission-rate comparison from the separate 1000-request Gateway spike case.
  if (exec.vu.iterationInScenario === 0 && startupSpreadSeconds > 0) {
    sleep(((exec.vu.idInTest - 1) / vus) * startupSpreadSeconds);
  }
  const account = users[exec.scenario.iterationInTest];
  if (!account) { flowErrors.add(1); exec.test.abort('fixture pool exhausted'); return; }
  startedUsers.add(1); flowErrors.add(0);
  user = account.userId;
  accessToken = account.accessToken;
  completed.add(0); heartbeatAccepted.add(0); heartbeatRace.add(0);
  registrationCompleted.add(0);
  const started = Date.now();
  const registerResponse = http.post(queue, null, params('queue_register'));
  if (flow === 'register') {
    const body = json(registerResponse);
    const ok = check(registerResponse, {
      'register: HTTP 200 SUCCESS and correct owner': r => r.status === 200 &&
        body?.code === 'SUCCESS' && body.data?.userId === user && body.data?.sessionId === session,
    });
    registrationCompleted.add(ok ? 1 : 0);
    registrationFailed.add(ok ? 0 : 1);
    flowErrors.add(ok ? 0 : 1);
    const elapsed = Date.now() - started;
    registrationElapsed.add(elapsed);
    // Record timings even on timeout; completed-phase k6 timings can be zero then.
    console.log('REGISTER_EVIDENCE ' + JSON.stringify({
      userId: user, ok, status: registerResponse.status,
      errorCode: registerResponse.error_code || null, error: registerResponse.error || null,
      elapsedMs: elapsed, timings: registerResponse.timings,
    }));
    return; // A failed registration must not abort the other 999 requests.
  }
  let state = statusOf(registerResponse, 'register');
  registrationCompleted.add(1);
  let nextHeartbeat = 0; // WAITING이면 첫 heartbeat를 즉시 보내고 이후 15초 간격
  while (state.status === 'WAITING') {
    requireCheck(Date.now() - started < deadlineMs, 'admission within diagnostic timeout');
    if (Date.now() >= nextHeartbeat) {
      const hb = http.post(`${queue}/me/heartbeat`, null, params('queue_heartbeat', {
        responseCallback: http.expectedStatuses(204, 409),
      }));
      if (hb.status === 204) {
        heartbeatAccepted.add(1);
        requireCheck(true, 'heartbeat accepted');
      } else {
        // 조회 직후 admission이 일어날 수 있다. 409를 무조건 정상 처리하지 않는다.
        requireCheck(hb.status === 409 && json(hb)?.code === 'Q-007', 'heartbeat conflict is state race');
        state = statusOf(http.get(`${queue}/me`, params('queue_status')), 'status after heartbeat conflict');
        requireCheck(state.status === 'ADMITTED', 'heartbeat conflict followed by ADMITTED');
        heartbeatRace.add(1);
      }
      nextHeartbeat = Date.now() + heartbeatMs;
    }
    if (state.status !== 'WAITING') break;
    sleep(pollSeconds);
    state = statusOf(http.get(`${queue}/me`, params('queue_status')), 'poll');
  }
  requireCheck(state.status === 'ADMITTED' && Boolean(state.admissionToken), 'valid admission received');
  if (mode !== 'vu') console.log('TOKEN_EVIDENCE ' + JSON.stringify({
    userId: user, sessionId: session, tokenHash: crypto.sha256(state.admissionToken, 'hex'), at: Date.now(),
  }));
  admissionWait.add(Date.now() - started); // 서버 admission 시각 자체가 아니라 polling으로 관측한 대기시간
  const seatParams = params('seat_list', {
    headers: requestHeaders({ 'X-Queue-Token': state.admissionToken }),
  });
  const response = http.get(`${base}/schedules/${session}/seats`, seatParams);
  const seats = json(response);
  requireCheck(response.status === 200 && seats?.code === 'SUCCESS', 'seat list HTTP 200');
  requireCheck(seats?.data?.seats?.some((seat) => seat.scheduleSeatId === seatId), 'fixture seat returned');
  const finalState = statusOf(http.get(`${queue}/me`, params('queue_status')), 'after seats');
  requireCheck(finalState.status === 'ENTERED', 'entry changed to ENTERED');
  completed.add(1);
  journeyTime.add(Date.now() - started);
  sleep(0.2); // fixed think time before replacing this completed user
}

export function handleSummary(data) {
  const value = (metric, key) => data.metrics[metric]?.values?.[key] ?? 0;
  return {
    '/results/k6-summary.json': JSON.stringify(data, null, 2),
    stdout: `Normal users completed: ${value('normal_journey_completed', 'count')}\n` +
      `Queue registrations completed: ${value('queue_registration_completed', 'count')}\n` +
      `HTTP requests: ${value('http_reqs', 'count')}\n` +
      `Heartbeat accepted: ${value('heartbeat_accepted', 'count')}\n` +
      `Admission race: ${value('heartbeat_admission_race', 'count')}\n` +
      `Journey duration ms: ${value('normal_journey_ms', 'avg')}\n`,
  };
}
