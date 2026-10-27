import https from 'node:https';
import type { AddressInfo } from 'node:net';
import { FAKE_CERT, FAKE_KEY } from './certs';

// 가짜 Live Client Data 서버
export type FakeLive = {
  port: number;
  setResponse: (path: string, body: unknown) => void;
  close: () => Promise<void>;
};

export async function startFakeLive(): Promise<FakeLive> {
  const responses = new Map<string, unknown>();

  const server = https.createServer({ cert: FAKE_CERT, key: FAKE_KEY }, (req, res) => {
    const body = responses.get(req.url ?? '');
    if (body === undefined) {
      res.statusCode = 404;
      res.end();
      return;
    }
    res.setHeader('Content-Type', 'application/json');
    res.end(JSON.stringify(body));
  });

  // 포트 0 = 운영체제가 비어 있는 포트를 골라 준다
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;

  return {
    port,
    setResponse: (path, body) => responses.set(path, body),
    close: () => new Promise((resolve) => server.close(() => resolve())),
  };
}
