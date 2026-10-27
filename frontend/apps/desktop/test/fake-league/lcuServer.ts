import https from 'node:https';
import type { AddressInfo } from 'node:net';
import type { Lockfile } from '../../src/shared/types';
import { FAKE_CERT, FAKE_KEY } from './certs';
import type { RawResponse } from './liveServer';

const PASSWORD = 'fake-password';

// DFGG가 롤 클라이언트에 보낸 요청 한 건
export type LcuRequest = { method: string; path: string; body: unknown };

// 가짜 LCU 서버
export type FakeLcu = {
  port: number;
  lockfile: Lockfile;
  setResponse: (method: string, path: string, body: unknown) => void;
  setRawResponse: (method: string, path: string, response: RawResponse) => void;
  requests: () => LcuRequest[];
  close: () => Promise<void>;
};

export async function startFakeLcu(): Promise<FakeLcu> {
  const responses = new Map<string, RawResponse>();
  const received: LcuRequest[] = [];
  const expectedAuth = `Basic ${Buffer.from(`riot:${PASSWORD}`).toString('base64')}`;

  const server = https.createServer({ cert: FAKE_CERT, key: FAKE_KEY }, (req, res) => {
    if (req.headers.authorization !== expectedAuth) {
      res.statusCode = 401;
      res.end();
      return;
    }

    let raw = '';
    req.setEncoding('utf-8');
    req.on('data', (chunk) => (raw += chunk));
    req.on('end', () => {
      const method = req.method ?? 'GET';
      const path = req.url ?? '';
      received.push({ method, path, body: raw ? JSON.parse(raw) : undefined });

      const response = responses.get(`${method} ${path}`);
      if (response === undefined) {
        res.statusCode = 404;
        res.end();
        return;
      }
      res.statusCode = response.status ?? 200;
      res.setHeader('Content-Type', 'application/json');
      res.end(response.body ?? '');
    });
  });

  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;

  return {
    port,
    lockfile: {
      name: 'LeagueClient',
      pid: '0',
      port: String(port),
      password: PASSWORD,
      protocol: 'https',
    },
    setResponse: (method, path, body) =>
      responses.set(`${method} ${path}`, { body: JSON.stringify(body) }),
    setRawResponse: (method, path, response) => responses.set(`${method} ${path}`, response),
    requests: () => [...received],
    close: () => new Promise((resolve) => server.close(() => resolve())),
  };
}
