import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type FakeBackend, startFakeBackend } from '../fake-backend/server';
import { type FakeLeague, startFakeLeague } from '../fake-league';
import { activePlayer, liveItem, livePlayer } from '../fixtures/live';

vi.mock('electron', async () => (await import('./electronMock.js')).electronMock);
vi.mock('../../src/main/sentry', () => ({ reportError: vi.fn() }));

const RECOMMEND_PATH = '/api/recommendations/v4';
const RECOMMENDATION = { recommendedItems: [{ id: 3031 }], situationalItems: [] };

async function loadApp() {
  vi.resetModules();
  return {
    testEnv: await import('../../src/main/testEnv.js'),
    connection: await import('../../src/main/lcu/connection.js'),
    poller: await import('../../src/main/live/poller.js'),
    state: await import('../../src/main/lcu/state.js'),
  };
}

describe('실시간 인게임 아이템 추천 테스트', () => {
  let league: FakeLeague;
  let backend: FakeBackend;
  let app: Awaited<ReturnType<typeof loadApp>>;

  const recommendRequests = () => backend.requests().filter((r) => r.path === RECOMMEND_PATH);
  const activePlayerRequests = () =>
    league.live.requests().filter((path) => path === '/liveclientdata/activeplayer').length;

  beforeEach(async () => {
    [league, backend] = await Promise.all([startFakeLeague(), startFakeBackend()]);
    app = await loadApp();
    app.testEnv.setTestEnv({ ...league.testEnv, apiUrl: backend.url });
    vi.stubEnv('PROGRAMDATA', league.env.PROGRAMDATA);

    // 롤 클라이언트: 게임 중, 패치 15.20
    league.lcu.setResponse('GET', '/lol-gameflow/v1/gameflow-phase', 'InProgress');
    league.lcu.setResponse('GET', '/lol-patch/v1/game-version', '15.20.700.1234');

    // 게임: 나(가렌, 미드, 롱소드 보유)와 상대(오공)
    league.live.setResponse('/liveclientdata/activeplayer', activePlayer({ currentGold: 1300 }));
    league.live.setResponse('/liveclientdata/playerlist', [
      livePlayer({ items: [liveItem({ itemID: 1036 })] }),
      livePlayer({
        riotId: 'enemy#KR1',
        summonerName: 'enemy',
        team: 'CHAOS',
        rawChampionName: 'game_character_displayname_MonkeyKing',
      }),
    ]);
    league.live.setResponse('/liveclientdata/gamestats', { gameTime: 300 });

    backend.setResponse(RECOMMEND_PATH, RECOMMENDATION);
  });

  afterEach(async () => {
    app.poller.stopLivePolling();
    app.connection.stopLcuConnection();
    app.testEnv.setTestEnv(null);
    vi.unstubAllEnvs();
    await Promise.all([league.close(), backend.close()]);
  });

  it('게임이 시작되면 내 챔피언, 상대, 인벤토리, 골드로 추천을 요청하고 상태에 담는다', async () => {
    app.poller.initLivePolling();
    app.connection.startLcuConnection();

    await vi.waitFor(() =>
      expect(app.state.getLcuState().recommendations).toEqual(RECOMMENDATION.recommendedItems),
    );

    expect(recommendRequests()[0]?.body).toMatchObject({
      myChampion: { name: 'Garen', position: 'MID' },
      enemies: [{ name: 'Wukong', position: 'MID' }],
      patch: '15.20',
      availableGold: 1300,
      inventoryItems: [{ itemId: 1036, quantity: 1 }],
    });
  });
  it('인벤토리가 그대로면 다시 요청하지 않는다', async () => {
    app.poller.initLivePolling();
    app.connection.startLcuConnection();

    await vi.waitFor(() => expect(recommendRequests()).toHaveLength(1));

    await vi.waitFor(() => expect(activePlayerRequests()).toBeGreaterThanOrEqual(3), {
      timeout: 6000,
    });

    expect(recommendRequests()).toHaveLength(1);
  });
  it('아이템을 사면 산 아이템을 담아 다시 요청한다', async () => {
    app.poller.initLivePolling();
    app.connection.startLcuConnection();

    await vi.waitFor(() =>
      expect(app.state.getLcuState().recommendations).toEqual(RECOMMENDATION.recommendedItems),
    );

    league.live.setResponse('/liveclientdata/playerlist', [
      livePlayer({ items: [liveItem({ itemID: 3031 })] }),
      livePlayer({
        riotId: 'enemy#KR1',
        summonerName: 'enemy',
        team: 'CHAOS',
        rawChampionName: 'game_character_displayname_MonkeyKing',
      }),
    ]);

    await vi.waitFor(() => expect(recommendRequests()).toHaveLength(2), { timeout: 4000 });

    expect(recommendRequests()[1]?.body).toMatchObject({ purchasedItemIds: [3031] });
  });
});
