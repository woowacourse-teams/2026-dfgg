import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { FAKE_CERT } from '../../../../test/fake-league/certs';
import { type FakeLive, startFakeLive } from '../../../../test/fake-league/liveServer';
import { setTestEnv } from '../../testEnv';
import { liveRequest } from '../client';

describe('liveRequest', () => {
  let live: FakeLive;

  beforeEach(async () => {
    live = await startFakeLive();
    setTestEnv({ riotCa: FAKE_CERT, livePort: live.port });
  });

  afterEach(async () => {
    setTestEnv(null);
    await live.close();
  });

  it('주입한 포트와 인증서로 가짜 Live 서버에 연결해 응답을 받는다', async () => {
    live.setResponse('/liveclientdata/gamestats', { gameTime: 120 });
    const stats = await liveRequest('/liveclientdata/gamestats');
    expect(stats).toEqual({ gameTime: 120 });
  });

  it('인증서를 주입하지 않으면 Riot 인증서만 믿어서 가짜 서버 연결을 거부한다', async () => {
    setTestEnv({ livePort: live.port });
    await expect(liveRequest('/liveclientdata/gamestats')).rejects.toThrow(/self-signed/);
  });

  it('응답이 없는 경로는 에러로 돌려준다', async () => {
    await expect(liveRequest('/liveclientdata/unkown')).rejects.toThrow(/LIVE 404/);
  });

  it('응답 본문이 비어 있으면 에러를 준다', async () => {
    live.setRawResponse('/liveclientdata/gamestats', { body: '' });
    await expect(liveRequest('/liveclientdata/activeplayer')).rejects.toThrow(/LIVE 404/);
  });

  it('응답이 Json이 아니면 파싱 에러를 준다', async () => {
    live.setRawResponse('/liveclientdata/gamestats', { body: '<html>loading</html>' });
    await expect(liveRequest('/liveclientdata/activeplayer')).rejects.toThrow(/LIVE 404/);
  });

  it('서버 에러면 상태 코드와 경로를 담은 에러로 돌려준다', async () => {
    live.setRawResponse('/liveclientdata/gamestats', { status: 503, body: 'not ready' });
    await expect(liveRequest('/liveclientdata/activeplayer')).rejects.toThrow('LIVE 404');
  });

  it('게임이 꺼져 있으면(서버 없음) 연결 에러로 돌려준다', async () => {
    await live.close();
    await expect(liveRequest('/liveclientdata/activeplayer')).rejects.toMatchObject({
      code: 'ECONNREFUSED',
    });
  });
});
