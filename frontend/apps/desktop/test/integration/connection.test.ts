import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type FakeLeague, startFakeLeague } from '../fake-league';
import { setTestEnv } from '../../src/main/testEnv';
import { getLockfileContent } from '../../src/main/lcu/lockfile';
import { lcuRequest } from '../../src/main/lcu/client';
import { liveRequest } from '../../src/main/live/client';
import { getDDragonData } from '../../src/main/live/ddragon';

vi.mock('../../src/main/sentry', () => ({ reportError: vi.fn() })); // ← 추가

describe('가짜 롤 연결', () => {
  let league: FakeLeague;

  beforeEach(async () => {
    league = await startFakeLeague();
    setTestEnv(league.testEnv);
    vi.stubEnv('PROGRAMDATA', league.env.PROGRAMDATA);
  });

  afterEach(async () => {
    vi.unstubAllEnvs();
    setTestEnv(null);
    await league.close();
  });

  it('DFGG가 스스로 찾은 lockfile로 가짜 롤 클라이언트에 접속한다', async () => {
    league.lcu.setResponse('GET', '/lol-gameflow/v1/gameflow-phase', 'Lobby');
    const lockfile = getLockfileContent();
    if (!lockfile) throw new Error('lockfile을 찾지 못했다');
    await expect(lcuRequest(lockfile, '/lol-gameflow/v1/gameflow-phase')).resolves.toBe('Lobby');
  });
  it('가짜 게임과 가짜 DDragon에도 연결된다', async () => {
    league.live.setResponse('/liveclientdata/gamestats', { gameTime: 60 });
    await expect(liveRequest('/liveclientdata/gamestats')).resolves.toEqual({ gameTime: 60 });

    const { championNames } = await getDDragonData();
    expect(championNames.MonkeyKing).toBe('Wukong');
  });
});
