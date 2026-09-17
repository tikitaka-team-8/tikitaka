import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';

const session = __ENV.SESSION_ID;
const users = JSON.parse(open('/private/users.json'));
let user;
let accessToken;
const seatId = __ENV.SEAT_ID;
if (!session || !seatId || !users.length) throw new Error('Missing test fixture');
const base = (__ENV.GATEWAY_URL || 'http://tikitaka-gateway:8000') + '/api/v1';
const queue = `${base}/event-sessions/${session}/queue`;
// 학습용 간격: 상태 조회 2초, heartbeat 15초. 실제 프런트 정책이 정해지면 맞춘다.
const pollSeconds = 2;
const heartbeatMs = 15000;
const deadlineMs = 60000;
const completed = new Counter('normal_journey_completed');
const heartbeatAccepted = new Counter('heartbeat_accepted');
const heartbeatRace = new Counter('heartbeat_admission_race');
const admissionWait = new Trend('admission_observed_wait_ms', true);
const journeyTime = new Trend('normal_journey_ms', true);

export const options = {
  scenarios: { normal: { executor: 'shared-iterations', vus: users.length, iterations: users.length,
    maxDuration: '90s', gracefulStop: '5s' } },
  thresholds: { checks: ['rate==1'], http_req_failed: ['rate==0'],
    normal_journey_completed: [`count==${users.length}`], iterations: [`count==${users.length}`] },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function params(name, extra = {}) {
  return { headers: { Authorization: `Bearer ${accessToken}` }, timeout: '5s', redirects: 0,
    tags: { name }, ...extra };
}
function requireCheck(condition, name) {
  if (!check(condition, { [name]: (ok) => ok })) fail(name);
}
function json(response) {
  try { return response.json(); } catch (_) { return null; }
}
function statusOf(response, name) {
  const body = json(response);
  requireCheck(response.status === 200 && body?.code === 'SUCCESS', `${name}: HTTP 200 SUCCESS`);
  requireCheck(body.data?.userId === user && body.data?.sessionId === session, `${name}: correct owner`);
  return body.data;
}

export default function () {
  const account = users[exec.scenario.iterationInTest];
  user = account.userId;
  accessToken = account.accessToken;
  completed.add(0); heartbeatAccepted.add(0); heartbeatRace.add(0);
  const started = Date.now();
  let state = statusOf(http.post(queue, null, params('queue_register')), 'register');
  console.log(`register: ${state.status}`);
  let nextHeartbeat = 0; // WAITING이면 첫 heartbeat를 즉시 보내고 이후 15초 간격
  while (state.status === 'WAITING') {
    requireCheck(Date.now() - started < deadlineMs, 'admission within 60s');
    if (Date.now() >= nextHeartbeat) {
      const hb = http.post(`${queue}/me/heartbeat`, null, params('queue_heartbeat', {
        responseCallback: http.expectedStatuses(204, 409),
      }));
      if (hb.status === 204) {
        heartbeatAccepted.add(1);
        requireCheck(true, 'heartbeat accepted');
        console.log('heartbeat: 204');
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
    console.log(`poll: ${state.status}`);
  }
  requireCheck(state.status === 'ADMITTED' && Boolean(state.admissionToken), 'valid admission received');
  admissionWait.add(Date.now() - started); // 서버 admission 시각 자체가 아니라 polling으로 관측한 대기시간
  const seatParams = params('seat_list', {
    headers: { Authorization: `Bearer ${accessToken}`, 'X-Queue-Token': state.admissionToken },
  });
  const response = http.get(`${base}/schedules/${session}/seats`, seatParams);
  const seats = json(response);
  requireCheck(response.status === 200 && seats?.code === 'SUCCESS', 'seat list HTTP 200');
  requireCheck(seats?.data?.seats?.some((seat) => seat.scheduleSeatId === seatId), 'fixture seat returned');
  const finalState = statusOf(http.get(`${queue}/me`, params('queue_status')), 'after seats');
  requireCheck(finalState.status === 'ENTERED', 'entry changed to ENTERED');
  completed.add(1);
  journeyTime.add(Date.now() - started);
  console.log('seat list: 200, fixture found; final state: ENTERED');
}

export function handleSummary(data) {
  const value = (metric, key) => data.metrics[metric]?.values?.[key] ?? 0;
  return {
    '/results/k6-summary.json': JSON.stringify(data, null, 2),
    stdout: `Normal users completed: ${value('normal_journey_completed', 'count')}\n` +
      `HTTP requests: ${value('http_reqs', 'count')}\n` +
      `Heartbeat accepted: ${value('heartbeat_accepted', 'count')}\n` +
      `Admission race: ${value('heartbeat_admission_race', 'count')}\n` +
      `Journey duration ms: ${value('normal_journey_ms', 'avg')}\n`,
  };
}
