import https from 'node:https';
import { RIOT_ROOT_CERT } from '../lcu/riotCert';

const LIVE_REQUEST_TIMEOUT_MS = 5000;

export function liveRequest<T>(endpoint: string): Promise<T | null> {
  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        hostname: `127.0.0.1`,
        port: 2999,
        path: endpoint,
        method: 'GET',
        ca: RIOT_ROOT_CERT,
        timeout: LIVE_REQUEST_TIMEOUT_MS,
      },
      (res) => {
        res.setEncoding('utf-8');
        let body = '';
        res.on('data', (chunk) => (body += chunk));
        res.on('end', () => {
          if (res.statusCode && res.statusCode >= 400) {
            reject(new Error(`LIVE ${res.statusCode} ${endpoint}: ${body.slice(0, 200)}`));
            return;
          }
          if (!body) {
            resolve(null);
            return;
          }
          try {
            resolve(JSON.parse(body));
          } catch {
            reject(new Error(`LIVE 응답 파싱 실패: ${body.slice(0, 200)}`));
          }
        });
      },
    );
    req.on('timeout', () => {
      req.destroy(new Error(`LIVE 요청 시간 초과: ${endpoint}`));
    });
    req.on('error', reject);
    req.end();
  });
}
