// 시나리오: 인기 공연의 동일 좌석(A-01)에 여러 사용자가 동시에 Seat Hold를 요청했을 때
// 정확히 한 명만 선점에 성공하고, 나머지는 설명 가능한 충돌 응답을 받는지 검증한다.
//
// 실행 전 준비:
//   1. docker-compose로 gateway 없이 ticketing-service(8082), payment-notification-service(8083)만
//      떠 있으면 된다 (이 스크립트는 Gateway/JWT를 거치지 않고 ticketing-service에 직접 X-User-Id로 요청한다 -
//      좌석 동시성 자체가 관심사이므로 인증 계층은 이번 테스트 범위에서 뺐다. Gateway까지 포함해서 재려면
//      README의 "Gateway를 포함하려면" 절 참고).
//   2. scripts/integration-test/seed/ticketing-seed.sql 을 한 번 적용해서 좌석 데이터를 넣어둔다.
//      기본값으로 쓰는 SEAT_ID(schedule_seat_id=...0001)가 seed 데이터의 "VIP A-1" 좌석이다.
//   3. 각 동시성 단계(10/50/100/300)를 돌리기 전에 scripts/test-scenarios/s02-seat-hold-concurrency/reset-seat-hold-race.sql 로 좌석/선점 상태를 리셋한다.
//
// 실행 예:
//   k6 run --env VUS=10  scripts/test-scenarios/s02-seat-hold-concurrency/seat-concurrency.js
//   k6 run --env VUS=50  scripts/test-scenarios/s02-seat-hold-concurrency/seat-concurrency.js
//   k6 run --env VUS=100 scripts/test-scenarios/s02-seat-hold-concurrency/seat-concurrency.js
//   k6 run --env VUS=300 scripts/test-scenarios/s02-seat-hold-concurrency/seat-concurrency.js   # 필요한 경우에만

import http from 'k6/http';
import { check, sleep, fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ─────────────────────────────────────────────
// 환경 변수
// ─────────────────────────────────────────────
const TICKETING_BASE_URL = __ENV.TICKETING_BASE_URL || 'http://localhost:8082';
const PAYMENT_BASE_URL = __ENV.PAYMENT_BASE_URL || 'http://localhost:8083';
const SESSION_ID = __ENV.SESSION_ID || '31000000-0000-0000-0000-000000000001';
const SEAT_ID = __ENV.SEAT_ID || '40000000-0000-0000-0000-000000000001'; // seed 데이터의 VIP A-1
const VUS = parseInt(__ENV.VUS || '10', 10);
// 매 실행마다 다른 userId 구간을 쓴다 - 같은 userId를 재사용하면 이전 실행에서 이미 ENTERED까지
// 끝난 사용자가 남아있어서(대기열 세션이 판매 종료+1시간까지 유지됨) 이번 setup()이 그 사용자를
// 다시 WAITING/ADMITTED로 착각하고 영원히 폴링하게 된다("N명이 ADMITTED 되지 못했습니다" 에러의
// 실제 원인). Date.now()를 기반으로 한 오프셋으로 구간을 자동으로 흩어둔다.
const USER_ID_BASE = parseInt(__ENV.USER_ID_BASE || String(700000000 + (Date.now() % 1000000)), 10);
const RUN_TAG = __ENV.RUN_TAG || `${Date.now()}`;

const ADMISSION_POLL_MAX_ROUNDS = parseInt(__ENV.ADMISSION_POLL_MAX_ROUNDS || '30', 10);
const ADMISSION_POLL_INTERVAL_SECONDS = parseFloat(__ENV.ADMISSION_POLL_INTERVAL_SECONDS || '1');

// ─────────────────────────────────────────────
// 커스텀 메트릭 - 주요 관측 항목(성공/충돌/예상하지 못한 실패/Timeout)을 그대로 카운터로 노출
// ─────────────────────────────────────────────
const holdSuccess = new Counter('seat_hold_success');
const holdConflict = new Counter('seat_hold_conflict'); // 설명 가능한 충돌 (409 SEAT_UNAVAILABLE 등)
const holdUnexpected = new Counter('seat_hold_unexpected'); // 그 외 실패 (5xx, 예상 못한 4xx)
const holdTimeout = new Counter('seat_hold_timeout');
const holdDuration = new Trend('seat_hold_duration', true);

const winnerFlowFailed = new Counter('winner_flow_failed'); // 선점 성공자의 예매/결제 단계 실패

export const options = {
  scenarios: {
    seat_hold_race: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '60s',
    },
  },
  thresholds: {
    // 좌석 선점 API 자체의 응답 지연 (p95/p99) - "주요 관측: Seat API p95·p99"
    'seat_hold_duration': ['p(95)<1000', 'p(99)<2000'],
    // 성공은 정확히 1건이어야 한다 (아래에서 handleSummary로 다시 한 번 엄격히 검증한다).
    'seat_hold_success': ['count>=1'],
    'seat_hold_unexpected': ['count==0'],
    'seat_hold_timeout': ['count==0'],
  },
};

// ─────────────────────────────────────────────
// setup(): 측정 대상(동시 Seat Hold 경쟁) 이전에, VUS명 전원을 대기열 ENTERED 상태로 만들어 둔다.
// "조건: 모든 사용자가 대기열 입장 조건 충족"을 실제로 재현하되, 대기열 승인 대기 시간이
// 좌석 동시성 지표에 섞여 들어가지 않도록 setup 단계(측정 제외 구간)에서 끝낸다.
// ─────────────────────────────────────────────
export function setup() {
  const users = [];
  for (let i = 0; i < VUS; i++) {
    users.push({ userId: USER_ID_BASE + i, admissionToken: null, entered: false });
  }

  // 1) 전원 대기열 진입 (WAITING 생성) - 빠르게 순회
  //    QueueStatus.isActive()는 WAITING/ADMITTED/ENTERED를 전부 "활성"으로 보기 때문에,
  //    (USER_ID_BASE 충돌 등으로) 이미 ENTERED까지 끝난 userId라면 서버가 200과 함께 그 기존
  //    엔트리를 그대로 돌려준다 - 이 경우 더 이상 할 일이 없으므로 즉시 entered=true로 처리한다.
  for (const user of users) {
    const res = http.post(
      `${TICKETING_BASE_URL}/api/v1/event-sessions/${SESSION_ID}/queue`,
      null,
      { headers: { 'X-User-Id': String(user.userId) }, tags: { name: 'queue_enter' } }
    );
    if (res.status !== 200) {
      fail(`[setup] 대기열 진입 실패 userId=${user.userId} status=${res.status} body=${res.body}`);
    }
    const body = JSON.parse(res.body);
    if (body.data.status === 'ADMITTED') {
      user.admissionToken = body.data.admissionToken;
    } else if (body.data.status === 'ENTERED') {
      user.entered = true;
    }
  }

  // 2) ADMITTED 될 때까지 라운드 단위로 폴링 (admission-batch-size=50 / admission-interval=1s 기준
  //    VUS=300이어도 약 6라운드 안에 전원 ADMITTED 되는 걸 감안해 넉넉히 잡음)
  for (let round = 0; round < ADMISSION_POLL_MAX_ROUNDS; round++) {
    const pending = users.filter((u) => !u.admissionToken && !u.entered);
    if (pending.length === 0) break;

    for (const user of pending) {
      const res = http.get(
        `${TICKETING_BASE_URL}/api/v1/event-sessions/${SESSION_ID}/queue/me`,
        { headers: { 'X-User-Id': String(user.userId) }, tags: { name: 'queue_status' } }
      );
      if (res.status !== 200) {
        fail(`[setup] 대기열 상태 조회 실패 userId=${user.userId} status=${res.status} body=${res.body}`);
      }
      const body = JSON.parse(res.body);
      if (body.data.status === 'ADMITTED') {
        user.admissionToken = body.data.admissionToken;
      } else if (body.data.status === 'ENTERED') {
        user.entered = true;
      }
    }
    sleep(ADMISSION_POLL_INTERVAL_SECONDS);
  }

  const stillWaiting = users.filter((u) => !u.admissionToken && !u.entered);
  if (stillWaiting.length > 0) {
    fail(`[setup] ${stillWaiting.length}명이 제한 시간 안에 ADMITTED 되지 못했습니다. ` +
      `ADMISSION_POLL_MAX_ROUNDS를 늘려보세요.`);
  }

  // 3) 입장 토큰으로 좌석 목록 조회를 한 번 호출해 ENTERED로 확정 (validateAndEnter 소비)
  //    1단계/2단계에서 이미 ENTERED로 확인된 사용자는 건너뛴다(토큰이 없어 호출할 수도 없다).
  for (const user of users) {
    if (user.entered) {
      continue;
    }
    const res = http.get(
      `${TICKETING_BASE_URL}/api/v1/schedules/${SESSION_ID}/seats`,
      {
        headers: { 'X-User-Id': String(user.userId), 'X-Queue-Token': user.admissionToken },
        tags: { name: 'queue_finalize_enter' },
      }
    );
    if (res.status !== 200) {
      fail(`[setup] 대기열 입장 확정 실패 userId=${user.userId} status=${res.status} body=${res.body}`);
    }
    user.entered = true;
  }

  console.log(`[setup] ${users.length}명 전원 대기열 ENTERED 완료. 동시 Seat Hold 요청을 시작합니다.`);

  return { users, runTag: RUN_TAG };
}

// ─────────────────────────────────────────────
// 측정 대상: 동일 좌석(SEAT_ID)에 대한 동시 Seat Hold 요청
// ─────────────────────────────────────────────
export default function (data) {
  const me = data.users[__VU - 1];
  if (!me) {
    fail(`[VU ${__VU}] setup에서 준비된 사용자를 찾지 못했습니다 (VUS 설정을 확인하세요).`);
  }

  const idempotencyKey = `race-${data.runTag}-${me.userId}`;

  const res = http.post(
    `${TICKETING_BASE_URL}/api/v1/schedules/${SESSION_ID}/seats/${SEAT_ID}/hold`,
    null,
    {
      headers: { 'X-User-Id': String(me.userId), 'Idempotency-Key': idempotencyKey },
      tags: { name: 'seat_hold' },
      timeout: '10s',
    }
  );
  holdDuration.add(res.timings.duration);

  if (res.status === 0) {
    // k6가 응답을 아예 못 받은 경우 (커넥션 타임아웃 등)
    holdTimeout.add(1);
    check(res, { '[unexpected] 요청이 timeout 없이 응답했다': () => false });
    return;
  }

  if (res.status === 201) {
    holdSuccess.add(1);
    check(res, { '선점 성공(201)': (r) => r.status === 201 });

    const seatHoldId = JSON.parse(res.body).data.seatHoldId;
    proceedToReservationAndPayment(me.userId, seatHoldId, data.runTag);
    return;
  }

  if (res.status === 409) {
    holdConflict.add(1);
    const body = JSON.parse(res.body);
    check(res, {
      '충돌 응답이 설명 가능한 코드(S-003 등)를 포함한다': () => typeof body.code === 'string' && body.code.length > 0,
    });
    return;
  }

  // 그 외에는 전부 "예상하지 못한 실패"로 집계 - 원인 파악을 위해 상태코드/응답 바디를 그대로 남긴다.
  holdUnexpected.add(1);
  console.error(`[unexpected] userId=${me.userId} status=${res.status} body=${res.body}`);
  check(res, { [`[unexpected] status=${res.status}`]: () => false });
}

// 좌석 선점에 성공한 단 한 명만 예매 생성 -> 결제 승인까지 이어서 진행한다.
// (이 구간은 "동시 경쟁" 측정과 무관한 후속 처리라 다른 VU의 동시 요청과는 시간이 겹치지 않는다.)
function proceedToReservationAndPayment(userId, seatHoldId, runTag) {
  const headers = { 'X-User-Id': String(userId), 'X-User-Role': 'USER' };

  const reservationRes = http.post(
    `${TICKETING_BASE_URL}/api/v1/reservations`,
    JSON.stringify({ seatHoldIds: [seatHoldId] }),
    {
      headers: {
        ...headers,
        'Content-Type': 'application/json',
        'Idempotency-Key': `race-rsv-${runTag}-${userId}`,
      },
      tags: { name: 'reservation_create' },
    }
  );

  const reservationOk = check(reservationRes, {
    '예매 생성 성공(201)': (r) => r.status === 201,
  });
  if (!reservationOk) {
    winnerFlowFailed.add(1);
    console.error(`[winner] 예매 생성 실패 status=${reservationRes.status} body=${reservationRes.body}`);
    return;
  }

  const reservationBody = JSON.parse(reservationRes.body).data;
  const paymentId = reservationBody.paymentId;

  const approveRes = http.post(
    `${PAYMENT_BASE_URL}/api/v1/payments/${paymentId}/approve`,
    JSON.stringify({ paymentMethod: 'CARD' }),
    {
      headers: { 'X-User-Id': String(userId), 'Content-Type': 'application/json' },
      tags: { name: 'payment_approve' },
    }
  );

  const approveOk = check(approveRes, {
    '결제 승인 성공(200)': (r) => r.status === 200,
  });
  if (!approveOk) {
    winnerFlowFailed.add(1);
    console.error(`[winner] 결제 승인 실패 status=${approveRes.status} body=${approveRes.body}`);
    return;
  }

  console.log(`[winner] userId=${userId} seatHoldId=${seatHoldId} reservationId=${reservationBody.reservationId} 예매/결제 완료. ` +
    `Kafka 결제 성공 이벤트 반영(CONFIRMED 전이)은 비동기이므로 scripts/test-scenarios/s02-seat-hold-concurrency/verify-seat-hold-race.sql 로 잠시 후 확인하세요.`);
}
