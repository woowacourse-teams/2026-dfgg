import https from 'node:https';
import type { AddressInfo } from 'node:net';
import { FAKE_CERT, FAKE_KEY } from './certs';

// 상태 코드와 본문 그대로
export type RawResponse = { status?: number; body?: string };

// 가짜 Live Client Data 서버
export type FakeLive = {
  port: number;
  setResponse: (path: string, body: unknown) => void;
  setRawResponse: (path: string, response: RawResponse) => void;
  close: () => Promise<void>;
};

export async function startFakeLive(): Promise<FakeLive> {
  const responses = new Map<string, RawResponse>();

  const server = https.createServer({ cert: FAKE_CERT, key: FAKE_KEY }, (req, res) => {
    const response = responses.get(req.url ?? '');
    if (response === undefined) {
      res.statusCode = 404;
      res.end();
      return;
    }
    res.statusCode = response.status ?? 200;
    res.setHeader('Content-Type', 'application/json');
    res.end(response.body ?? '');
  });

  // 포트 0 = 운영체제가 비어 있는 포트를 골라 준다
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;

  return {
    port,
    setResponse: (path, body) => responses.set(path, { body: JSON.stringify(body) }),
    setRawResponse: (path, response) => responses.set(path, response),
    close: () => new Promise((resolve) => server.close(() => resolve())),
  };
}
