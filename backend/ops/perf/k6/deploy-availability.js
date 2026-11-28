// 배포 중 무중단 확인. 가벼운 API를 일정한 속도로 부르고 실패한 요청을 시각과 함께 남긴다.
// 배포를 시작하기 전에 켜 두고, 배포가 끝난 뒤 멈춘다.
//
//   BASE_URL=http://<dev-ec2>:8080 k6 run -e RPS=5 -e DURATION=15m backend/ops/perf/k6/deploy-availability.js
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL;
if (!BASE_URL) {
  throw new Error('BASE_URL이 필요하다 (예: http://3.36.100.9:8080)');
}

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RPS || 5),
      timeUnit: '1s',
      duration: __ENV.DURATION || '15m',
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
  // 실패가 하나라도 있으면 k6가 0이 아닌 코드로 끝난다.
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

const body = JSON.stringify({ myPosition: 'MID', enemies: [103] });
const params = {
  headers: { 'Content-Type': 'application/json', 'Accept-Language': 'ko' },
  timeout: '10s',
};

export default function () {
  const res = http.post(`${BASE_URL}/api/counters`, body, params);
  const ok = check(res, { 'status 200': (r) => r.status === 200 });
  if (!ok) {
    console.error(`${new Date().toISOString()} status=${res.status} error=${res.error || '-'}`);
  }
}
