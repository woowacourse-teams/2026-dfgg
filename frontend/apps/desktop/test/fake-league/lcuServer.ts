import https from 'node:https';
import { IncomingMessage } from 'node:http';
import { WebSocket, WebSocketServer } from 'ws';
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
  // 이 주소의 이벤트를 구독 중인 DFGG에게 보낸다.
  emit: (uri: string, data: unknown, eventType?: string) => void;
  // DFGG가 이 이벤트를 구독할 때까지 기다린다.
  waitForSubscription: (eventName: string) => Promise<void>;
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

  // 같은 포트에서 WebSocket도 받는다. 진짜도 HTTPS와 WebSocket을 한 포트로 연다.
  const subscriptions = new Map<WebSocket, Set<string>>();
  const waiters: { eventName: string; resolve: () => void }[] = [];

  const wss = new WebSocketServer({
    server,
    verifyClient: ({ req }: { req: IncomingMessage }) => req.headers.authorization === expectedAuth,
  });

  wss.on('connection', (socket) => {
    subscriptions.set(socket, new Set());
    socket.on('message', (raw) => {
      const [opcode, eventName] = JSON.parse(raw.toString());
      if (opcode !== 5) return; // 5는 구독 신청
      subscriptions.get(socket)?.add(eventName);
      for (const waiter of waiters.filter((w) => w.eventName === eventName)) waiter.resolve();
    });
    socket.on('close', () => subscriptions.delete(socket));
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
    emit: (uri, data, eventType = 'Update') => {
      // '/lol-gameflow/v1/gameflow-phase' → 'OnJsonApiEvent_lol-gameflow_v1_gameflow-phase'
      const eventName = `OnJsonApiEvent${uri.replaceAll('/', '_')}`;
      for (const [socket, names] of subscriptions) {
        // 구독 이름이 이벤트 이름의 앞부분이면 받는다. current-summoner 로 summoner-profile 까지 받는 것처럼
        const subscribed = [...names].find((name) => eventName.startsWith(name));
        if (subscribed) socket.send(JSON.stringify([8, subscribed, { uri, eventType, data }]));
      }
    },
    waitForSubscription: (eventName) =>
      new Promise((resolve) => {
        if ([...subscriptions.values()].some((names) => names.has(eventName))) return resolve();
        waiters.push({ eventName, resolve });
      }),
    close: async () => {
      // 연결된 WebSocket 을 먼저 끊어야 서버가 닫힌다
      for (const socket of wss.clients) socket.terminate();
      await new Promise<void>((resolve) => wss.close(() => resolve()));
      await new Promise<void>((resolve) => server.close(() => resolve()));
    },
  };
}
