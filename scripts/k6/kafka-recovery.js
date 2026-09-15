import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Rate } from 'k6/metrics';

const VU_COUNT = 100; // 가상 사용자 100명
const ITERATIONS_PER_VU = 10; // 각 사용자당 10번의 각각 다른 결제 승인 => 총 요청 1000건
const PAYMENT_BASE_URL = __ENV.PAYMENT_BASE_URL || 'http://localhost:8083';

// csv 데이터 로드
const users = new SharedArray('s08 users', () => parseCsv(open('./data/s08-users.csv')));
const approvals = new SharedArray('s08 payment approvals', () =>
    parseCsv(open('./data/s08-payment-approvals.csv')),
);

const paymentApprovalSuccess = new Rate('payment_approval_success');

validateFixtures();

// 실행 방식
export const options = {
    scenarios: {
        payment_approval: { // 100명의 사용자가 각자 10개의 결제를 승인
            executor: 'per-vu-iterations',
            vus: VU_COUNT,
            iterations: ITERATIONS_PER_VU,
            maxDuration: '5m',
        },
    },
    thresholds: { // 성공 기준
        http_req_failed: ['rate<0.01'], // http 오류율 1% 미만
        payment_approval_success: ['rate>0.99'], // 결제 승인 성공률 99% 초과
        http_req_duration: ['p(95)<2000'], // 전체 요청 95%가 2초 이내에 응답
    },
};

export default function () { // vu와 csv 데이터 매핑
    const vuIndex = exec.vu.idInTest - 1;
    const iterationIndex = exec.vu.iterationInScenario;
    const user = users[vuIndex];
    const approval = approvals[(vuIndex * ITERATIONS_PER_VU) + iterationIndex];

    // 첫 요청을 0~990ms 사이에 분산하여 100 VU의 1초 Ramp-up을 재현. (100명이 약 1초에 걸쳐 진입, 첫 요청 이후에는 각자 남은 결제를 연속 처리)
    if (iterationIndex === 0) {
        sleep(Number(user.start_offset_ms) / 1000);
    }

    // 실제 요청
    const response = http.post(
        `${PAYMENT_BASE_URL}/api/v1/payments/${approval.payment_id}/approve`,
        JSON.stringify({ paymentMethod: approval.payment_method }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': approval.user_id,
            },
            tags: { name: 'POST /api/v1/payments/:paymentId/approve' },
        },
    );

    let approved = false;
    try {
        approved = response.json('data.status') === 'APPROVED';
    } catch (_) {
        approved = false;
    }
    // 성공 판정
    const succeeded = response.status === 200 && approved;
    paymentApprovalSuccess.add(succeeded);

    check(response, {
        'payment approval returns HTTP 200': (result) => result.status === 200,
        'payment status is APPROVED': () => approved,
    });
}

function parseCsv(content) {
    const [headerLine, ...lines] = content.trim().split(/\r?\n/);
    const headers = headerLine.split(',');

    return lines.filter((line) => line.length > 0).map((line) => {
        const values = line.split(',');
        return Object.fromEntries(headers.map((header, index) => [header, values[index]]));
    });
}

// 실행 전 csv 검증
function validateFixtures() {
    if (users.length !== VU_COUNT) {
        throw new Error(`s08-users.csv must contain ${VU_COUNT} users, but found ${users.length}`);
    }
    if (approvals.length !== VU_COUNT * ITERATIONS_PER_VU) {
        throw new Error(
            `s08-payment-approvals.csv must contain ${VU_COUNT * ITERATIONS_PER_VU} approvals, `
            + `but found ${approvals.length}`,
        );
    }

    for (let vuIndex = 0; vuIndex < VU_COUNT; vuIndex += 1) {
        const user = users[vuIndex];

        for (let iterationIndex = 0; iterationIndex < ITERATIONS_PER_VU; iterationIndex += 1) {
            const approval = approvals[(vuIndex * ITERATIONS_PER_VU) + iterationIndex];
            const expectedVuId = String(vuIndex + 1);
            const expectedIteration = String(iterationIndex + 1);

            if (user.vu_id !== expectedVuId
                || approval.vu_id !== expectedVuId
                || approval.iteration !== expectedIteration
                || approval.user_id !== user.user_id) {
                throw new Error(
                    `CSV mapping mismatch at VU ${expectedVuId}, iteration ${expectedIteration}`,
                );
            }
        }
    }
}
