import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FakeLcu, startFakeLcu } from '../../../../test/fake-league/lcuServer';
import { setTestEnv } from '../../testEnv';
import { FAKE_CERT } from '../../../../test/fake-league/certs';
import { connectLcuSocket } from '../socket';
import type { WebSocket } from 'ws';
import type { LcuEvent } from '../../../shared/types';

const PHASE_EVENT = 'OnJsonApiEvent_lol-gameflow_v1_gameflow-phase';
const SUMMONER_EVENT = 'OnJsonApiEvent_lol-summoner_v1_current-summoner';
const CHAMP_SELECT_EVENT = 'OnJsonApiEvent_lol-champ-select_v1_session';

vi.mock('../../sentry', () => ({ reportError: vi.fn() }));

describe('connectLcuSocket', () => {
  let lcu: FakeLcu;
  let socket: WebSocket | null = null;

  beforeEach(async () => {
    lcu = await startFakeLcu();
    setTestEnv({ riotCa: FAKE_CERT });
  });

  afterEach(async () => {
    socket?.close();
    setTestEnv(null);
    await lcu.close();
  });

  it('연결되면 phase, 소환사, 밴픽 이벤트를 구독한다', async () => {
    socket = connectLcuSocket(lcu.lockfile, () => {});
    await lcu.waitForSubscription(PHASE_EVENT);
    await lcu.waitForSubscription(SUMMONER_EVENT);
    await lcu.waitForSubscription(CHAMP_SELECT_EVENT);
  });
  it('구독한 이벤트가 오면 주소와 데이터를 넘겨준다', async () => {
    const received = new Promise<LcuEvent>((resolve) => {
      socket = connectLcuSocket(lcu.lockfile, resolve);
    });
    await lcu.waitForSubscription(PHASE_EVENT);
    lcu.emit('/lol-gameflow/v1/gameflow-phase', 'ChampSelect');
    expect(await received).toMatchObject({
      uri: '/lol-gameflow/v1/gameflow-phase',
      data: 'ChampSelect',
    });
  });
  it('구독한 주소의 하위 주소 이벤트도 받는다', async () => {
    const received = new Promise<LcuEvent>((resolve) => {
      socket = connectLcuSocket(lcu.lockfile, resolve);
    });
    await lcu.waitForSubscription(SUMMONER_EVENT);
    lcu.emit('/lol-summoner/v1/current-summoner/summoner-profile', { backgroundSkinId: 1 });
    expect(await received).toMatchObject({
      uri: '/lol-summoner/v1/current-summoner/summoner-profile',
      data: { backgroundSkinId: 1 },
    });
  });
  it('비밀번호가 틀리면 연결이 거부된다', async () => {
    const error = new Promise<Error>((resolve) => {
      socket = connectLcuSocket({ ...lcu.lockfile, password: 'wrong' }, () => {});
      socket.on('error', resolve);
    });
    expect((await error).message).toMatch(/401/);
  });
  it('비밀번호가 틀리면 연결이 거부된다', async () => {
    socket?.close();
    await lcu.close();
    await expect(connectLcuSocket(lcu.lockfile, () => {})).rejects.toMatchObject({
      code: 'ECONNREFUSED',
    });
  });
});
