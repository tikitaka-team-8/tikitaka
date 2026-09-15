import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Rate } from 'k6/metrics';

const VU_COUNT = 100;
const USERS_PER_VU = 6;
const EXPECTED_USER_COUNT = 600;
const EXPECTED_APPROVAL_COUNT = 1000;
const PAYMENT_BASE_URL = __ENV.PAYMENT_BASE_URL || 'http://localhost:8083';

const users = new SharedArray('s08 failure users', () =>
    parseCsv(open('./data/s08-failure-users.csv')),
);
const approvals = new SharedArray('s08 failure payment approvals', () =>
    parseCsv(open('./data/s08-failure-payment-approvals.csv')),
);

const paymentApprovalSuccess = new Rate('payment_approval_success');
const approvalsByUser = groupApprovalsByUser(approvals);

validateFixtures();

export const options = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        payment_approval: {
            executor: 'per-vu-iterations',
            vus: VU_COUNT,
            iterations: USERS_PER_VU,
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
    const userIterationIndex = exec.vu.iterationInScenario;
    const user = users[(vuIndex * USERS_PER_VU) + userIterationIndex];
    const userApprovals = approvalsByUser.get(userKey(user.vu_id, user.user_iteration));

    // 100 VU의 첫 진입을 1초 동안 분산
    if (userIterationIndex === 0) {
        sleep(Number(user.start_offset_ms) / 1000);
    }

    // 사용자별 결제 1건 또는 2건을 같은 시점에 승인 요청
    const responses = http.batch(userApprovals.map((approval) => [
        'POST',
        `${PAYMENT_BASE_URL}/api/v1/payments/${approval.payment_id}/approve`,
        JSON.stringify({ paymentMethod: approval.payment_method }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': approval.user_id,
            },
            tags: { name: 'POST /api/v1/payments/:paymentId/approve' },
        },
    ]));

    responses.forEach((response) => {
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

function groupApprovalsByUser(approvalRows) {
    const grouped = new Map();

    approvalRows.forEach((approval) => {
        const key = userKey(approval.vu_id, approval.user_iteration);
        const userApprovals = grouped.get(key) || [];
        userApprovals.push(approval);
        grouped.set(key, userApprovals);
    });

    return grouped;
}

function userKey(vuId, userIteration) {
    return `${vuId}:${userIteration}`;
}

function validateFixtures() {
    if (users.length !== EXPECTED_USER_COUNT) {
        throw new Error(`s08-failure-users.csv must contain ${EXPECTED_USER_COUNT} users, but found ${users.length}`);
    }
    if (approvals.length !== EXPECTED_APPROVAL_COUNT) {
        throw new Error(
            `s08-failure-payment-approvals.csv must contain ${EXPECTED_APPROVAL_COUNT} approvals, `
            + `but found ${approvals.length}`,
        );
    }

    users.forEach((user, index) => {
        const expectedVuId = String(Math.floor(index / USERS_PER_VU) + 1);
        const expectedUserIteration = String((index % USERS_PER_VU) + 1);
        const userApprovals = approvalsByUser.get(userKey(user.vu_id, user.user_iteration)) || [];
        const expectedApprovalCount = Number(user.user_iteration) <= 4 ? 2 : 1;
        const expectedRequestSlots = expectedApprovalCount === 2 ? '1,2' : '1';
        const actualRequestSlots = userApprovals.map((approval) => approval.request_slot).sort().join(',');

        if (user.vu_id !== expectedVuId
            || user.user_iteration !== expectedUserIteration
            || userApprovals.length !== expectedApprovalCount
            || actualRequestSlots !== expectedRequestSlots
            || userApprovals.some((approval) => approval.user_id !== user.user_id)) {
            throw new Error(
                `CSV mapping mismatch at VU ${expectedVuId}, user iteration ${expectedUserIteration}`,
            );
        }
    });

    const reservationIds = new Set(approvals.map((approval) => approval.reservation_id));
    const paymentIds = new Set(approvals.map((approval) => approval.payment_id));
    if (reservationIds.size !== EXPECTED_APPROVAL_COUNT || paymentIds.size !== EXPECTED_APPROVAL_COUNT) {
        throw new Error('Reservation ID and Payment ID must be unique for every approval');
    }
}
