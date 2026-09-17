import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        concurrent_payment_approve: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 1,
            maxDuration: '10s',
        },
    },
};

const BASE_URL = __ENV.BASE_URL;
const PAYMENT_ID = __ENV.PAYMENT_ID;
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN;

export default function () {
    const url = `${BASE_URL}/api/v1/payments/${PAYMENT_ID}/approve`;

    const payload = JSON.stringify({
        paymentMethod: 'CARD',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${ACCESS_TOKEN}`,
        },
        tags: {
            name: 'S03 concurrent payment approve',
        },
    };

    const response = http.post(url, payload, params);

    check(response, {
        '승인 요청이 처리 가능한 응답이다': (r) =>
            r.status === 200 || r.status === 409,
    });

    console.log(
        `[VU ${__VU}] status=${response.status} body=${response.body}`
    );
}