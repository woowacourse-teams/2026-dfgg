import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('electron', async () => (await import('./electronMock.js')).electronMock);
vi.mock('../../src/main/sentry', () => ({ reportError: vi.fn() }));

import { type FakeBackend, startFakeBackend } from '../fake-backend/server';
import { type FakeLeague, startFakeLeague } from '../fake-league';
import {
  lcuGame,
  lcuIdentity,
  lcuMatchHistory,
  lcuParticipant,
  lcuStats,
} from '../fixtures/matchHistory';

const DETAIL_PATH = '/lol-match-history/v1/games/1001';
const DF_SCORE_PATH = '/api/df-score';

async function loadApp() {
  vi.resetModules();
  return {
    testEnv: await import('../../src/main/testEnv.js'),
    service: await import('../../src/main/lcu/service.js'),
  };
}

describe('게임 종료 흐름', () => {
  let league: FakeLeague;
  let backend: FakeBackend;
  let app: Awaited<ReturnType<typeof loadApp>>;

  const dfScoreRequests = () => backend.requests().filter((r) => r.path === DF_SCORE_PATH);

  beforeEach(async () => {
    [league, backend] = await Promise.all([startFakeLeague(), startFakeBackend()]);
    app = await loadApp();
    app.testEnv.setTestEnv({ ...league.testEnv, apiUrl: backend.url });
    vi.stubEnv('PROGRAMDATA', league.env.PROGRAMDATA);
  });

  afterEach(async () => {
    app.testEnv.setTestEnv(null);
    vi.unstubAllEnvs();
    await Promise.all([league.close(), backend.close()]);
  });

  it('최근 전적을 화면용 데이터로 바꿔 돌려준다', async () => {
    // 목록 응답: 나(5번 슬롯) 한 명만, 미니언 150 + 정글 몹 30
    const me = lcuParticipant(5, {
      championId: 61,
      stats: lcuStats({ win: true, kills: 7, totalMinionsKilled: 150, neutralMinionsKilled: 30 }),
    });
    league.lcu.setResponse(
      'GET',
      '/lol-match-history/v1/products/lol/current-summoner/matches',
      lcuMatchHistory([lcuGame({ participants: [me], participantIdentities: [lcuIdentity(5)] })]),
    );
    league.lcu.setResponse('GET', '/lol-game-queues/v1/queues', [
      { id: 420, name: '개인/2인 랭크 게임', shortName: '솔로랭크', gameMode: 'CLASSIC' },
    ]);

    const matches = await app.service.fetchMatchHistory();

    expect(matches).toHaveLength(1);
    expect(matches?.[0]).toMatchObject({
      gameId: 1001,
      queueName: '솔로랭크',
      win: true,
      championId: 61,
      stats: { kills: 7, cs: 180 },
    });
  });

  it('경기 상세로 DF 스코어를 요청하고 참가자별 순위를 붙인다', async () => {
    league.lcu.setResponse('GET', DETAIL_PATH, lcuGame());
    backend.setResponse(DF_SCORE_PATH, {
      participants: [
        { puuid: 'puuid-1', score: 70, badge: null },
        { puuid: 'puuid-6', score: 90, badge: 'ACE' },
        { puuid: 'puuid-2', score: 80, badge: 'MVP' },
      ],
    });

    const scores = await app.service.fetchMatchDfScores(1001);

    expect(dfScoreRequests()[0]?.body).toMatchObject({ queueId: 420, gameDurationSec: 1800 });
    expect(dfScoreRequests()[0]?.body).toHaveProperty('participants.length', 10);
    expect(scores).toEqual({
      6: { score: 90, rank: 1 },
      2: { score: 80, rank: 2 },
      1: { score: 70, rank: 3 },
    });
  });

  it('같은 판의 DF 스코어는 다시 요청하지 않는다', async () => {
    league.lcu.setResponse('GET', DETAIL_PATH, lcuGame());
    backend.setResponse(DF_SCORE_PATH, {
      participants: [{ puuid: 'puuid-1', score: 70, badge: null }],
    });

    await app.service.fetchMatchDfScores(1001);
    await app.service.fetchMatchDfScores(1001);

    expect(dfScoreRequests()).toHaveLength(1);
  });
});
