import { afterEach, beforeEach, describe, it, vi } from 'vitest';
import { type FakeLeague, startFakeLeague } from '../fake-league';

vi.mock('electron', async () => (await import('./electronMock.js')).electronMock);
vi.mock('../../src/main/sentry', () => ({ reportError: vi.fn() }));

/** 연결 상태(connection.ts)를 켜고 끄는 테스트라, 테스트마다 앱을 새로 불러온다 */
async function loadApp() {
  vi.resetModules();
  return {
    testEnv: await import('../../src/main/testEnv.js'),
    connection: await import('../../src/main/lcu/connection.js'),
  };
}

describe('앱 종료', () => {
  let league: FakeLeague;
  let app: Awaited<ReturnType<typeof loadApp>>;

  beforeEach(async () => {
    league = await startFakeLeague();
    app = await loadApp();
    app.testEnv.setTestEnv(league.testEnv);
    vi.stubEnv('PROGRAMDATA', league.env.PROGRAMDATA);
  });

  afterEach(async () => {
    app.testEnv.setTestEnv(null);
    vi.unstubAllEnvs();
    await league.close();
  });

  // E2E 에서 앱을 끌 때 "WebSocket was closed before the connection was established" 에러 창이 떠서 찾은 버그
  it('연결 중에 앱을 꺼도 에러 없이 끝난다', async () => {
    app.connection.startLcuConnection();
    app.connection.stopLcuConnection(); // 소켓이 아직 연결 중일 때 바로 끈다

    // ws 는 error 를 바로 내지 않고 다음 순간에 낸다. 그때까지 기다린다
    await new Promise((resolve) => setTimeout(resolve, 100));
  });
});
