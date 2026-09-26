// 특정 회차의 좌석 조회 API를 여러 명이 반복 호출하면서, 응답시간·실패율·응답 크기 등을 측정하는 k6 테스트 코드
//
// 큐 토큰은 (sessionId, userId) 쌍에 묶여서 발급되므로 VU마다 자기 몫의 토큰이 필요합니다.
// 예전에는 python(mint-queue-tokens.py)으로 미리 발급해서 TOKENS_FILE로 넘겼지만,
// 이제는 setup() 단계에서 k6 자체의 http.batch()로 필요한 만큼 병렬 발급합니다.
// (python/pip/venv 불필요 - k6 하나만 있으면 됩니다)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// ================= 환경 변수 =================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const SESSION_ID = __ENV.SESSION_ID || '31000000-0000-0000-0000-000000000001';
const USE_FILTER = (__ENV.USE_FILTER || 'false') === 'true';
// 실제 API 파라미터는 section / grade 입니다 (status 필터는 존재하지 않음 - SeatController 확인됨)
const FILTER_QUERY = __ENV.FILTER_QUERY || 'section=VIP';
// 좌석 목록 API에 페이지네이션(page/size)이 추가됨 - 기본은 API 기본값(page=0,size=50)을 그대로 씀.
// PAGE_SIZE를 0 이하로 주면 이전처럼 페이지네이션 파라미터를 안 붙여서 API 기본 size(50)로 조회.
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 50);
const PAGE_NUMBER = Number(__ENV.PAGE_NUMBER || 0);
const SCENARIO = __ENV.SCENARIO || 'smoke'; // smoke | compare | load
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || '5s';

// load 시나리오 전용: 이번 실행에서 고정할 VU 수 1개 (단계별로 wrapper 스크립트가 반복 호출)
const LOAD_VUS = Number(__ENV.LOAD_VUS || 50);
// 기본값은 admission-token-ttl(기본 180s)보다 mint 시간을 더해도 확실히 짧도록
// ramp+유지+ramp-down 합계를 100s로 낮춰뒀습니다 (실측: 1000 VU에서 기존 기본값 200s는
// TTL을 넘겨 후반부 요청이 Q-001로 대량 실패했음).
const LOAD_RAMP = __ENV.LOAD_RAMP || '20s';
const LOAD_DURATION = __ENV.LOAD_DURATION || '1m';

// ================= 토큰 발급(mint) 관련 환경 변수 =================
// 재실행 시 userId가 겹치면 이미 ENTERED 상태라 재발급이 막힐 수 있어, 매 실행마다 Date.now() 기반으로
// 유니크한 범위를 자동으로 씁니다. 필요하면 START_USER_ID로 직접 지정할 수도 있습니다.
const START_USER_ID = Number(__ENV.START_USER_ID || Date.now());
const MINT_ENQUEUE_CHUNK = Number(__ENV.MINT_ENQUEUE_CHUNK || 200); // 큐 진입 요청을 한 번에 몇 개씩 병렬 호출할지
const MINT_POLL_INTERVAL_S = Number(__ENV.MINT_POLL_INTERVAL_S || 1);
// 이전 TOKENS_FILE 방식도 그대로 남겨뒀습니다(수동 디버깅용). 지정 안 하면 자동 발급을 씁니다.
const TOKENS_FILE = __ENV.TOKENS_FILE || '';

function requiredUserCount() {
    if (SCENARIO === 'smoke') return 1;
    if (SCENARIO === 'compare') return Number(__ENV.COMPARE_VUS || 10);
    return LOAD_VUS;
}
const REQUIRED_USERS = requiredUserCount();
// 서버 admission-batch-size(기본 50)/admission-interval(기본 1s) 기준 최소 소요시간 + 여유
const MINT_TIMEOUT_S = Number(
    __ENV.MINT_TIMEOUT_S || Math.max(30, Math.ceil(REQUIRED_USERS / 40) + 30)
);

// ================= 커스텀 메트릭 =================
const responseSize = new Trend('seat_response_size', false);
const businessSuccess = new Counter('business_success');
const timeoutCount = new Counter('timeout_count');

// ================= 시나리오 정의 =================
const scenarioDefs = {
    smoke: {
        executor: 'shared-iterations',
        vus: 1,
        iterations: 1,
        maxDuration: '10s',
    },
    compare: {
        executor: 'constant-vus',
        vus: Number(__ENV.COMPARE_VUS || 10),
        duration: __ENV.COMPARE_DURATION || '1m',
    },
    load: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { duration: LOAD_RAMP, target: LOAD_VUS },
            { duration: LOAD_DURATION, target: LOAD_VUS },
            { duration: LOAD_RAMP, target: 0 },
        ],
    },
};

export const options = {
    scenarios: { [SCENARIO]: scenarioDefs[SCENARIO] },
    // 토큰 발급(setup)이 끝날 때까지 k6가 기다려주는 최대 시간. mint 로직 자체 타임아웃보다 여유 있게 잡음.
    setupTimeout: `${MINT_TIMEOUT_S + 30}s`,
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(95)', 'p(99)'],
};

// ================= 토큰 발급(mint) =================
// 실제 큐 API(enterQueue -> queue/me 폴링)를 k6 http.batch()로 병렬 호출해서
// [{userId, token}, ...] 를 만듭니다. python 스크립트가 하던 일을 그대로 k6 안으로 옮긴 것입니다.
function mintTokens(count, startUserId) {
    let pending = [];
    for (let i = 0; i < count; i++) {
        pending.push({ userId: String(startUserId + i) });
    }

    // 1) 큐 진입(enterQueue) - 청크 단위로 병렬 호출 (실패해도 폴링 단계에서 자연히 걸러짐)
    for (let offset = 0; offset < pending.length; offset += MINT_ENQUEUE_CHUNK) {
        const chunk = pending.slice(offset, offset + MINT_ENQUEUE_CHUNK);
        const reqs = chunk.map((u) => ({
            method: 'POST',
            url: `${BASE_URL}/api/v1/event-sessions/${SESSION_ID}/queue`,
            params: { headers: { 'X-User-Id': u.userId }, timeout: '10s' },
        }));
        http.batch(reqs);
    }

    // 2) ADMITTED 될 때까지 폴링 (admission-batch-size/interval 스로틀링 때문에 여러 라운드 필요)
    const admitted = [];
    const deadline = Date.now() + MINT_TIMEOUT_S * 1000;
    while (pending.length > 0 && Date.now() < deadline) {
        const reqs = pending.map((u) => ({
            method: 'GET',
            url: `${BASE_URL}/api/v1/event-sessions/${SESSION_ID}/queue/me`,
            params: { headers: { 'X-User-Id': u.userId }, timeout: '10s' },
        }));
        const responses = http.batch(reqs);
        const stillPending = [];
        for (let i = 0; i < pending.length; i++) {
            const res = responses[i];
            let data;
            try {
                data = JSON.parse(res.body).data;
            } catch (e) {
                stillPending.push(pending[i]);
                continue;
            }
            if (data && data.status === 'ADMITTED' && data.admissionToken) {
                admitted.push({ userId: pending[i].userId, token: data.admissionToken });
            } else if (data && data.status === 'EXPIRED') {
                // 포기 (재시도 안 함)
            } else {
                stillPending.push(pending[i]);
            }
        }
        pending = stillPending;
        if (pending.length > 0) {
            sleep(MINT_POLL_INTERVAL_S);
        }
    }

    if (pending.length > 0) {
        console.warn(`[mint] 타임아웃으로 ${pending.length}명 발급 실패 (성공 ${admitted.length}명으로 진행)`);
    }
    return admitted;
}

export function setup() {
    if (TOKENS_FILE) {
        // 수동 디버깅용: 예전처럼 미리 만들어둔 토큰 파일을 쓰고 싶으면 TOKENS_FILE을 지정하세요.
        return { tokenPool: JSON.parse(open(TOKENS_FILE)) };
    }

    console.log(`[mint] ${REQUIRED_USERS}명분 큐 토큰 발급 시작 (startUserId=${START_USER_ID}, timeout=${MINT_TIMEOUT_S}s)`);
    const tokenPool = mintTokens(REQUIRED_USERS, START_USER_ID);
    console.log(`[mint] 발급 완료: 성공 ${tokenPool.length}/${REQUIRED_USERS}`);

    if (tokenPool.length === 0) {
        throw new Error('발급된 큐 토큰이 0개입니다 - BASE_URL/SESSION_ID 및 큐 서버 상태를 확인하세요');
    }

    return { tokenPool };
}

export default function (data) {
    const tokenPool = data.tokenPool;
    const identity = tokenPool[(__VU - 1) % tokenPool.length];
    const userId = identity.userId;
    const token = identity.token;

    let url = `${BASE_URL}/api/v1/schedules/${SESSION_ID}/seats`;
    const queryParams = [];
    if (USE_FILTER) {
        queryParams.push(FILTER_QUERY);
    }
    queryParams.push(`page=${PAGE_NUMBER}`, `size=${PAGE_SIZE}`);
    url += `?${queryParams.join('&')}`;

    const params = {
        headers: {
            'X-User-Id': userId,
            'X-Queue-Token': token,
        },
        tags: {
            filter: USE_FILTER ? 'on' : 'off',
        },
        timeout: REQUEST_TIMEOUT,
    };

    const res = http.get(url, params);

    const ok = check(res, {
        'status is 200': (r) => r.status === 200,
    });

    if (res.status === 0) {
        timeoutCount.add(1);
    }
    if (ok) {
        businessSuccess.add(1);
    }

    responseSize.add(res.body ? res.body.length : 0);

    if (SCENARIO === 'smoke') {
        console.log(
            `status=${res.status}, duration=${res.timings.duration}ms, size=${res.body ? res.body.length : 0}bytes`
        );
    }
}
