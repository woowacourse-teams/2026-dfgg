import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { FakeLcu, startFakeLcu } from '../../../../test/fake-league/lcuServer';
import { setTestEnv } from '../../testEnv';
import { FAKE_CERT } from '../../../../test/fake-league/certs';
import { lcuRequest } from '../client';

describe('lcuRequest', () => {
  let lcu: FakeLcu;

  beforeEach(async () => {
    lcu = await startFakeLcu();
    setTestEnv({ riotCa: FAKE_CERT });
  });

  afterEach(async () => {
    setTestEnv(null);
    await lcu.close();
  });

  it('lockfile 의 포트와 비밀번호로 접속해 응답을 받는다', async () => {
    lcu.setResponse('GET', '/lol-summoner/v1/current-summoner', { gameName: 'me', tagLine: 'KR1' });
    const summoner = await lcuRequest(lcu.lockfile, '/lol-summoner/v1/current-summoner');
    expect(summoner).toEqual({ gameName: 'me', tagLine: 'KR1' });
  });
  it('비밀번호가 틀리면 401 에러로 돌려준다', async () => {
    const wrong = { ...lcu.lockfile, password: 'wrong' };
    await expect(lcuRequest(wrong, '/lol-summoner/v1/current-summoner')).rejects.toThrow(/LCU 401/);
  });
  it('PATCH 요청은 메서드, 경로, 본문을 그대로 보낸다', async () => {
    lcu.setRawResponse('PATCH', '/lol-champ-select/v1/session/actions/3', { status: 204 });
    await lcuRequest(lcu.lockfile, '/lol-champ-select/v1/session/actions/3', {
      method: 'PATCH',
      body: { championId: 61 },
    });
    expect(lcu.requests()).toEqual([
      { method: 'PATCH', path: '/lol-champ-select/v1/session/actions/3', body: { championId: 61 } },
    ]);
  });
  it('메서드를 정하지 않으면 GET 으로 본문 없이 보낸다', async () => {
    lcu.setResponse('GET', '/lol-gameflow/v1/gameflow-phase', 'Lobby');
    await lcuRequest(lcu.lockfile, '/lol-gameflow/v1/gameflow-phase');
    expect(lcu.requests()).toEqual([
      { body: undefined, method: 'GET', path: '/lol-gameflow/v1/gameflow-phase' },
    ]);
  });
  it('롤 클라이언트가 꺼져 있으면(서버 없음) 연결 에러로 돌려준다', async () => {
    await lcu.close();
    await expect(
      lcuRequest(lcu.lockfile, '/lol-summoner/v1/current-summoner'),
    ).rejects.toMatchObject({
      code: 'ECONNREFUSED',
    });
  });
});
