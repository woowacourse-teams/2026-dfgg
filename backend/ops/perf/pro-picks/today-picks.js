// 오늘의 추천 픽(POST /api/today-picks) API 전체 응답 시간. DB 구간 측정(run.sh)의 차이가 전체에서 차지하는 비율을 보려고 잰다.
// 부하가 아니라 지연을 보므로 VU 수를 고정한다(VU마다 응답을 받고 바로 다음 요청). 라인은 다섯 개 중 무작위, 밴 없음.
//
//   k6 run -e BASE_URL=http://10.0.0.231:8080 -e VUS=1 -e DURATION=60s \
//          --summary-export=results/<UTC>-k6-vu1.json backend/ops/perf/pro-picks/today-picks.js

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const POSITIONS = ['TOP', 'JUNGLE', 'MID', 'BOTTOM', 'SUPPORT'];
const PARAMS = {
    headers: { 'Content-Type': 'application/json', 'Accept-Language': 'ko-KR' },
    timeout: '10s',
};

export const options = {
    scenarios: {
        latency: {
            executor: 'constant-vus',
            vus: Number(__ENV.VUS || 1),
            duration: __ENV.DURATION || '60s',
        },
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    thresholds: { checks: ['rate>0.99'] },
};

export default function () {
    const position = POSITIONS[Math.floor(Math.random() * POSITIONS.length)];
    const res = http.post(`${BASE_URL}/api/today-picks`, JSON.stringify({ position }), PARAMS);
    check(res, {
        '200': (r) => r.status === 200,
        'picks 있음': (r) => r.status === 200 && r.json('picks').length > 0,
    });
}
