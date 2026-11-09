import http from 'node:http';
import type { AddressInfo } from 'node:net';

// DFGG가 백엔드에 보낸 요청
export type BackendRequest = { path: string; body: unknown; headers: http.IncomingHttpHeaders };

// 가짜 DFGG 백엔드.
export type FakeBackend = {
  url: string;
  setResponse: (path: string, body: unknown, status?: number) => void;
  requests: () => BackendRequest[];
  close: () => Promise<void>;
};

export async function startFakeBackend(): Promise<FakeBackend> {
  const responses = new Map<string, { status: number; body: unknown }>();
  const received: BackendRequest[] = [];

  const server = http.createServer((req, res) => {
    let raw = '';
    req.setEncoding('utf-8');
    req.on('data', (chunk) => (raw += chunk));
    req.on('end', () => {
      const path = req.url ?? '';
      received.push({ path, body: raw ? JSON.parse(raw) : undefined, headers: req.headers });

      const response = responses.get(path);
      if (response === undefined) {
        res.statusCode = 404;
        res.end();
        return;
      }
      res.statusCode = response.status;
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify(response.body));
    });
  });

  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;

  return {
    url: `http://127.0.0.1:${port}`,
    setResponse: (path, body, status = 200) => responses.set(path, { status, body }),
    requests: () => [...received],
    close: () => new Promise((resolve) => server.close(() => resolve())),
  };
}
