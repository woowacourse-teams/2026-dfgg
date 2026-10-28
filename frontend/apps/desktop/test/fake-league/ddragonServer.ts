import http from 'node:http';
import type { AddressInfo } from 'node:net';
import type { DDragonItem } from '../../src/main/live/types';

export type FakeDDragonData = {
  version: string;
  champions: { id: string; name: string }[];
  items: Record<string, DDragonItem>;
};

// 가짜 DDragon 서버.
export type FakeDDragon = {
  url: string;
  requests: () => string[];
  close: () => Promise<void>;
};

export async function startFakeDDragon(data: FakeDDragonData): Promise<FakeDDragon> {
  const { version, champions, items } = data;
  const received: string[] = [];

  // DDragon 이 주는 세 가지 응답. ddragon.ts 가 읽는 모양만 맞춘다
  const responses: Record<string, unknown> = {
    '/api/versions.json': [version],
    [`/cdn/${version}/data/en_US/champion.json`]: {
      data: Object.fromEntries(champions.map((champion) => [champion.id, champion])),
    },
    [`/cdn/${version}/data/en_US/item.json`]: { data: items },
  };

  const server = http.createServer((req, res) => {
    const path = req.url ?? '';
    received.push(path);

    const body = responses[path];
    if (body === undefined) {
      res.statusCode = 404;
      res.end();
      return;
    }
    res.setHeader('Content-Header', 'application/json');
    res.end(JSON.stringify(body));
  });

  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address() as AddressInfo;

  return {
    url: `http://127.0.0.1:${port}`,
    requests: () => [...received],
    close: () => new Promise((resolve) => server.close(() => resolve())),
  };
}
