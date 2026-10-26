import { describe, expect, it } from 'vitest';

import type { LcuChampSelectSession } from '../lcu/champSelect.types';
import { sameIds, toChampSelectInfo } from './session';

const ME = 2;

function session(overrides: Partial<LcuChampSelectSession> = {}): LcuChampSelectSession {
  return {
    localPlayerCellId: ME,
    myTeam: [
      { cellId: 0, championId: 64, assignedPosition: 'jungle' },
      { cellId: 1, championId: 0, assignedPosition: 'top' },
      { cellId: ME, championId: 61, assignedPosition: 'middle' },
    ],
    theirTeam: [
      { cellId: 5, championId: 103, assignedPosition: '' },
      { cellId: 6, championId: 0, assignedPosition: '' },
    ],
    actions: [
      [
        { actorCellId: 0, championId: 157, completed: true, type: 'ban' },
        { actorCellId: 5, championId: 238, completed: true, type: 'ban' },
        { actorCellId: 6, championId: 0, completed: false, type: 'ban' },
      ],
      [{ actorCellId: ME, championId: 61, completed: false, type: 'pick' }],
    ],
    ...overrides,
  };
}

describe('toChampSelectInfo', () => {
  it('내 포지션을 백엔드 이름으로 바꾸고 고른 챔피언을 읽는다', () => {
    const info = toChampSelectInfo(session());

    expect(info.position).toBe('MID');
    expect(info.myChampionId).toBe(61);
    expect(info.locked).toBe(false);
  });

  it('아군은 나를 빼고, 아직 안 고른 자리(0)는 뺀다', () => {
    const info = toChampSelectInfo(session());

    expect(info.allies).toEqual([64]);
    expect(info.enemies).toEqual([103]);
  });

  it('끝난 밴 행동만 밴으로 친다', () => {
    expect(toChampSelectInfo(session()).bans).toEqual([157, 238]);
  });

  it('밴 행동이 없는 큐는 bans 칸을 본다. 같은 챔피언을 양 팀이 밴하면 둘 다 남긴다', () => {
    const info = toChampSelectInfo(
      session({ actions: [], bans: { myTeamBans: [157], theirTeamBans: [157, 0] } }),
    );

    expect(info.bans).toEqual([157, 157]);
  });

  it('내 픽 행동이 끝나면 확정으로 본다', () => {
    const locked = session({
      actions: [[{ actorCellId: ME, championId: 61, completed: true, type: 'pick' }]],
    });

    expect(toChampSelectInfo(locked).locked).toBe(true);
  });

  it('배정 포지션이 없으면(블라인드·칼바람) 포지션도 고른 챔피언도 비운다', () => {
    const blind = session({
      myTeam: [{ cellId: ME, championId: 0, assignedPosition: '' }],
    });
    const info = toChampSelectInfo(blind);

    expect(info.position).toBeNull();
    expect(info.myChampionId).toBeNull();
  });
});

describe('sameIds', () => {
  it('순서까지 같아야 같다', () => {
    expect(sameIds([1, 2], [1, 2])).toBe(true);
    expect(sameIds([1, 2], [2, 1])).toBe(false);
    expect(sameIds(undefined, [])).toBe(true);
  });
});
