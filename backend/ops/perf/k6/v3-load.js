// v3 추천 부하 테스트. 시나리오는 SCENARIO로 고른다.
//
//   smoke  : 1 RPS × 1분. 스크립트·환경 확인용. 에러 0건이어야 다음 단계로 간다
//   target : WARMUP 동안 TARGET_RPS로 데운 뒤 DURATION 동안 TARGET_RPS를 유지한다. 목표 판정은 이 구간만 본다
//   ramp   : 0에서 RAMP_MAX_RPS까지 RAMP_STEPS단계로 올리며 단계마다 RAMP_STEP씩 유지한다. 지연이 꺾이는 지점을 찾는다
//
//   k6 run -e BASE_URL=http://127.0.0.1:8080 -e SCENARIO=target -e TARGET_RPS=10 \
//          --summary-export=results/<UTC>-before/k6-summary.json backend/ops/perf/k6/v3-load.js
//
// 도착률(arrival-rate) 방식이다. 서버가 느려져도 정해진 RPS로 계속 보낸다 — VU 방식은 느려지면 덜 보내서
// 느려진 것이 가려진다. 보낼 VU가 모자라면 dropped_iterations가 쌓이고, 그 회차는 목표 부하를 다 못 보낸 것이다.

import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const SCENARIO = __ENV.SCENARIO || 'smoke';
const TARGET_RPS = Number(__ENV.TARGET_RPS || 10);
const DURATION = __ENV.DURATION || '5m';
const WARMUP = __ENV.WARMUP || '2m';
const RAMP_MAX_RPS = Number(__ENV.RAMP_MAX_RPS || TARGET_RPS * 3);
const RAMP_STEPS = Number(__ENV.RAMP_STEPS || 6);
const RAMP_STEP = __ENV.RAMP_STEP || '1m';
const V3_RATIO = Number(__ENV.V3_RATIO || 1.0);
const P95_MS = Number(__ENV.P95_MS || 1000);
const ERROR_RATE = Number(__ENV.ERROR_RATE || 0.01);
const MAX_VUS = Number(__ENV.MAX_VUS || 200);
const REQUESTS = __ENV.REQUESTS || './data/requests.json';

// 모든 VU가 한 벌을 나눠 쓴다. VU마다 1,000건을 복사하지 않는다.
const requests = new SharedArray('requests', () => JSON.parse(open(REQUESTS)));

const PARAMS = {
    headers: { 'Content-Type': 'application/json', 'Accept-Language': 'ko-KR' },
    timeout: '30s',
};

function constantRate(rate, duration, phase, startTime = '0s') {
    return {
        executor: 'constant-arrival-rate',
        rate,
        timeUnit: '1s',
        duration,
        startTime,
        preAllocatedVUs: Math.min(MAX_VUS, Math.max(5, rate * 2)),
        maxVUs: MAX_VUS,
        tags: { phase },
    };
}

// 단계마다 5초 동안 올리고 RAMP_STEP 동안 유지한다. 단계 번호를 태그로 남겨 단계별 p95를 요약에 띄운다.
function rampStages() {
    const stages = [];
    for (let step = 1; step <= RAMP_STEPS; step++) {
        const rate = Math.round((RAMP_MAX_RPS * step) / RAMP_STEPS);
        stages.push({ target: rate, duration: '5s' }, { target: rate, duration: RAMP_STEP });
    }
    return stages;
}

const SCENARIOS = {
    smoke: { smoke: constantRate(1, '1m', 'measure') },
    target: {
        warmup: constantRate(TARGET_RPS, WARMUP, 'warmup'),
        measure: constantRate(TARGET_RPS, DURATION, 'measure', WARMUP),
    },
    ramp: {
        ramp: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            stages: rampStages(),
            preAllocatedVUs: Math.min(MAX_VUS, Math.max(5, RAMP_MAX_RPS)),
            maxVUs: MAX_VUS,
            tags: { phase: 'measure' },
        },
    },
};

if (!SCENARIOS[SCENARIO]) {
    throw new Error(`SCENARIO는 ${Object.keys(SCENARIOS).join('|')} 중 하나다: ${SCENARIO}`);
}

// 측정 구간 시나리오의 이름. dropped_iterations에는 시나리오에 붙인 태그(phase)가 달리지 않아
// scenario 이름으로만 거를 수 있다 — phase로 거르면 항상 0이 나와 판정이 늘 통과한다(2026-10-12 재현).
const MEASURE_SCENARIO = { smoke: 'smoke', target: 'measure', ramp: 'ramp' }[SCENARIO];

// 판정은 측정 구간(phase:measure)만 본다. 워밍업은 캐시를 데우는 구간이라 목표 판정에 넣지 않는다.
function thresholds() {
    const base = {
        'http_req_duration{phase:measure,endpoint:v3}': [`p(95)<${P95_MS}`],
        'http_req_failed{phase:measure}': [`rate<${ERROR_RATE}`],
        [`dropped_iterations{scenario:${MEASURE_SCENARIO}}`]: ['count==0'],
    };
    if (SCENARIO !== 'ramp') {
        return base;
    }
    // ramp는 꺾이는 지점을 보려는 것이라 단계별 p95를 요약에 띄운다. 판정 자체는 의미가 없어 상한을 넉넉히 둔다.
    for (let step = 1; step <= RAMP_STEPS; step++) {
        base[`http_req_duration{endpoint:v3,ramp_step:${step}}`] = ['p(95)<600000'];
    }
    return base;
}

export const options = {
    scenarios: SCENARIOS[SCENARIO],
    thresholds: thresholds(),
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    discardResponseBodies: true,
};

// ramp에서 지금 몇 단계인지. 단계 길이(올림 5초 + 유지)를 시작 후 경과 시간으로 나눈다.
function currentRampStep() {
    const stepMillis = 5000 + durationMillis(RAMP_STEP);
    const elapsed = exec.instance.currentTestRunDuration;
    return Math.min(RAMP_STEPS, Math.floor(elapsed / stepMillis) + 1);
}

function durationMillis(text) {
    const match = /^(\d+)(ms|s|m|h)$/.exec(text);
    if (!match) {
        throw new Error(`기간 형식은 숫자+단위(ms|s|m|h)다: ${text}`);
    }
    const unit = { ms: 1, s: 1000, m: 60000, h: 3600000 }[match[2]];
    return Number(match[1]) * unit;
}

function tagsFor(endpoint) {
    const tags = { endpoint };
    if (SCENARIO === 'ramp') {
        tags.ramp_step = String(currentRampStep());
    }
    return tags;
}

export default function () {
    if (Math.random() >= V3_RATIO) {
        const response = http.get(`${BASE_URL}/api/champions`, { ...PARAMS, tags: tagsFor('champions') });
        check(response, { 'champions 200': (r) => r.status === 200 });
        return;
    }
    // 테스트 전체에서 순서대로 돌려 쓴다. 회차가 달라도 같은 순서로 같은 요청이 나간다.
    const body = requests[exec.scenario.iterationInTest % requests.length];
    const response = http.post(`${BASE_URL}/api/recommendations/v3`, JSON.stringify(body), {
        ...PARAMS,
        tags: tagsFor('v3'),
    });
    check(response, { 'v3 200': (r) => r.status === 200 });
}
