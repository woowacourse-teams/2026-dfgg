import { describe, expect, it, vi } from 'vitest';

import { isOverlayGame } from '../queue';

// service 는 electron·sentry 를 불러와서 테스트에서는 비워 둔다 (vi.mock 은 import 보다 먼저 적용된다)
vi.mock('../service', () => ({ fetchGameflowSession: vi.fn() }));

const session = (
  queue: { id: number; gameMode: string },
  extra: { isCustomGame?: boolean; map?: { id: number; gameMode: string } } = {},
) => ({
  gameData: { gameId: 1, queue, isCustomGame: extra.isCustomGame },
  map: extra.map,
});

describe('isOverlayGame', () => {
  it('솔로 랭크면 띄운다', () => {
    expect(isOverlayGame(session({ id: 420, gameMode: 'CLASSIC' }))).toBe(true);
  });

  it('아수라장이면 띄우지 않는다', () => {
    expect(isOverlayGame(session({ id: 3270, gameMode: 'KIWI' }))).toBe(false);
  });

  it('협곡 사용자 설정 게임이면 띄운다', () => {
    const custom = session(
      { id: -1, gameMode: '' },
      { isCustomGame: true, map: { id: 11, gameMode: 'CLASSIC' } },
    );
    expect(isOverlayGame(custom)).toBe(true);
  });

  it('map 이 없으면 queue 의 모드로 판단한다', () => {
    expect(isOverlayGame(session({ id: -1, gameMode: 'CLASSIC' }, { isCustomGame: true }))).toBe(
      true,
    );
  });

  it('칼바람 사용자 설정 게임이면 띄우지 않는다', () => {
    const aram = session(
      { id: -1, gameMode: '' },
      { isCustomGame: true, map: { id: 12, gameMode: 'ARAM' } },
    );
    expect(isOverlayGame(aram)).toBe(false);
  });

  it('세션을 못 읽으면 띄우지 않는다', () => {
    expect(isOverlayGame(null)).toBe(false);
  });
});
