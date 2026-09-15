import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Rate } from 'k6/metrics';

const VU_COUNT = 100;
const ITERATIONS_PER_VU = 10;
const PAYMENT_BASE_URL = __ENV.PAYMENT_BASE_URL || 'http://localhost:8083';

const users = new SharedArray('s08 users', () => parseCsv(open('./data/s08-users.csv')));
const approvals = new SharedArray('s08 payment approvals', () =>
    parseCsv(open('./data/s08-payment-approvals.csv')),
);

const paymentApprovalSuccess = new Rate('payment_approval_success');

validateFixtures();

export const options = {
    scenarios: {
        payment_approval: {
            executor: 'per-vu-iterations',
            vus: VU_COUNT,
            iterations: ITERATIONS_PER_VU,
            maxDuration: '5m',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        payment_approval_success: ['rate>0.99'],
        http_req_duration: ['p(95)<2000'],
    },
};

export default function () {
    const vuIndex = exec.vu.idInTest - 1;
    const iterationIndex = exec.vu.iterationInScenario;
    const user = users[vuIndex];
    const approval = approvals[(vuIndex * ITERATIONS_PER_VU) + iterationIndex];

    // 첫 요청을 0~990ms 사이에 분산하여 100 VU의 1초 Ramp-up을 재현
    if (iterationIndex === 0) {
        sleep(Number(user.start_offset_ms) / 1000);
    }

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
