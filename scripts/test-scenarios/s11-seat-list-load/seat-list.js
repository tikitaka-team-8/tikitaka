import http from 'k6/http';
import { check } from 'k6';

const SESSION_ID =
    __ENV.SESSION_ID || '31000000-0000-0000-0000-000000000001';

const QUEUE_TOKEN =
    __ENV.QUEUE_TOKEN || '실제큐토큰';

export const options = {
    vus: 1,
    iterations: 1,
};

export default function () {
    const url =
        `http://localhost:8082/api/v1/schedules/${SESSION_ID}/seats`;

    const params = {
        headers: {
            'X-User-Id': '1',
            'X-Queue-Token': QUEUE_TOKEN,
        },
    };

    const res = http.get(url, params);

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    console.log(
        `status=${res.status}, duration=${res.timings.duration}ms`
    );
}