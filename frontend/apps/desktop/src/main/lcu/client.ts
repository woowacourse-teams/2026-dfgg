import { Lockfile } from '../../shared/types';
import https from 'node:https';
import { RIOT_ROOT_CERT } from './riotCert';

const LCU_REQUEST_TIMEOUT_MS = 5000;

type LcuRequestOptions = { method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'; body?: unknown };

// lcu api 요청 함수. 기본은 GET, 룬 페이지처럼 바꾸는 요청은 method 와 body 를 넘긴다.
export function lcuRequest<T>(
  lockfile: Lockfile,
  endpoint: string,
  { method = 'GET', body: requestBody }: LcuRequestOptions = {},
): Promise<T | null> {
  const { port, password } = lockfile;
  const auth = Buffer.from(`riot:${password}`).toString('base64');
  const payload = requestBody === undefined ? undefined : JSON.stringify(requestBody);

  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        hostname: `127.0.0.1`,
        port: port,
        path: endpoint,
        method,
        headers: {
          Authorization: `Basic ${auth}`,
          ...(payload && {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(payload),
          }),
        },
        ca: RIOT_ROOT_CERT,
        timeout: LCU_REQUEST_TIMEOUT_MS,
      },
      (res) => {
        res.setEncoding('utf-8');
        let body = '';
        res.on('data', (chunk) => (body += chunk));
        res.on('end', () => {
          if (res.statusCode && res.statusCode >= 400) {
            reject(new Error(`LCU ${res.statusCode} ${endpoint}: ${body.slice(0, 200)}`));
            return;
          }
          if (!body) {
            resolve(null);
            return;
          }
          try {
            resolve(JSON.parse(body) as T);
          } catch {
            reject(new Error(`LCU 응답 파싱 실패: ${body.slice(0, 200)}`));
          }
        });
      },
    );
    req.on('timeout', () => {
      req.destroy(new Error(`LCU 요청 시간 초과: ${endpoint}`));
    });
    req.on('error', reject);
    req.end(payload);
  });
}
