import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type FakeBackend, startFakeBackend } from '../fake-backend/server';
import { type FakeLeague, startFakeLeague } from '../fake-league';
import { champSelectSession, COUNTERS, player, RUNES, TODAY_PICKS } from '../fixtures/champSelect';

vi.mock('electron', async () => (await import('./electronMock.js')).electronMock);
vi.mock('../../src/main/sentry', () => ({ reportError: vi.fn() }));

const SESSION_PATH = '/lol-champ-select/v1/session';

async function loadApp() {
  vi.resetModules();
  return {
    testEnv: await import('../../src/main/testEnv.js'),
    connection: await import('../../src/main/lcu/connection.js'),
    champSelect: await import('../../src/main/champSelect/champSelect.js'),
  };
}

describe('밴픽 흐름', () => {
  let league: FakeLeague;
  let backend: FakeBackend;
  let app: Awaited<ReturnType<typeof loadApp>>;

  beforeEach(async () => {
    [league, backend] = await Promise.all([startFakeLeague(), startFakeBackend()]);
    app = await loadApp();
    app.testEnv.setTestEnv({ ...league.testEnv, apiUrl: backend.url });
    vi.stubEnv('PROGRAMDATA', league.env.PROGRAMDATA);

    // 롤 클라이언트: 지금 밴픽 중이고, 이런 세션이다
    league.lcu.setResponse('GET', '/lol-gameflow/v1/gameflow-phase', 'ChampSelect');
    league.lcu.setResponse('GET', SESSION_PATH, champSelectSession());
    league.lcu.setResponse('GET', '/lol-game-data/assets/v1/champion-summary.json', [
      { id: 61, alias: 'Orianna' },
    ]);
    league.lcu.setResponse('GET', '/lol-perks/v1/pages', []);
    league.lcu.setResponse('GET', '/lol-perks/v1/inventory', { ownedPageCount: 5 });
    league.lcu.setRawResponse('POST', '/lol-perks/v1/pages', { status: 200, body: '{}' });

    // 백엔드: 세 추천
    backend.setResponse('/api/today-picks', TODAY_PICKS);
    backend.setResponse('/api/counters', COUNTERS);
    backend.setResponse('/api/recommendations/runes', RUNES);
  });

  afterEach(async () => {
    app.connection.stopLcuConnection();
    app.testEnv.setTestEnv(null);
    vi.unstubAllEnvs();
    await Promise.all([league.close(), backend.close()]);
  });

  describe('밴픽 통합 테스트', () => {
    it('밴픽에 들어가면 내 포지션, 상대, 아군, 밴으로 카운터를 요청하고 화면 상태에 담는다', async () => {
      app.champSelect.initChampSelect();
      app.connection.startLcuConnection();

      await vi.waitFor(() =>
        expect(app.champSelect.getChampSelectState()?.counters).toEqual(COUNTERS),
      );

      expect(backend.requests().find((r) => r.path === '/api/counters')?.body).toEqual({
        myPosition: 'MID',
        enemies: [234],
        allies: [64],
        bans: [157],
      });
    });

    it('룬 추천을 받으면 1순위 룬 페이지를 롤 클라이언트에 넣는다', async () => {
      app.champSelect.initChampSelect();
      app.connection.startLcuConnection();

      await vi.waitFor(() => expect(app.champSelect.getChampSelectState()?.applied).toBe(1));

      expect(league.lcu.requests()).toContainEqual({
        method: 'POST',
        path: '/lol-perks/v1/pages',
        body: {
          name: 'DFGG',
          primaryStyleId: 8200,
          subStyleId: 8300,
          selectedPerkIds: [8229, 8226, 8210, 8237, 8304, 8345, 5008, 5008, 5001],
          current: true,
        },
      });
    });

    it('카운터 요청이 실패해도 오늘의 픽과 룬은 화면 상태에 담긴다', async () => {
      backend.setResponse('/api/counters', { message: 'error' }, 500);

      app.champSelect.initChampSelect();
      app.connection.startLcuConnection();

      await vi.waitFor(() => expect(app.champSelect.getChampSelectState()?.runes).toEqual(RUNES));

      const state = app.champSelect.getChampSelectState();
      expect(state?.todayPicks).toEqual(TODAY_PICKS);
      expect(state?.runes).toEqual(RUNES);
      expect(state?.counters).toBeNull();
    });

    it('상대가 새로 픽하면 카운터를 다시 요청한다', async () => {
      app.champSelect.initChampSelect();
      app.connection.startLcuConnection();
      await vi.waitFor(() =>
        expect(app.champSelect.getChampSelectState()?.counters).toEqual(COUNTERS),
      );

      league.lcu.emit(
        SESSION_PATH,
        champSelectSession({
          theirTeam: [
            player({ cellId: 5, championId: 234 }),
            player({ cellId: 6, championId: 103 }),
          ],
        }),
      );

      await vi.waitFor(() => {
        const counterRequests = backend.requests().filter((r) => (r.path = '/api/counters'));
        expect(counterRequests.at(-1)?.body).toMatchObject({ enemies: [234, 103] });
      });
    });
  });
});
