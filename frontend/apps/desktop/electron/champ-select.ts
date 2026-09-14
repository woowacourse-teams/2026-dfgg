import type { Lineup, LineupSlot, Position } from './types';

/** LCU가 쓰는 포지션 문자열 → 백엔드 포지션 */
const POSITION_BY_LCU: Record<string, Position> = {
  top: 'TOP',
  jungle: 'JUNGLE',
  middle: 'MID',
  bottom: 'BOTTOM',
  utility: 'SUPPORT',
};

interface LcuPlayer {
  cellId?: number;
  championId?: number;
  /** 확정 전에 올려둔 챔피언. 확정되면 championId 로 옮겨간다. */
  championPickIntent?: number;
  assignedPosition?: string;
}

interface LcuSession {
  localPlayerCellId?: number;
  myTeam?: LcuPlayer[];
  theirTeam?: LcuPlayer[];
}

/** 확정 픽이 없으면 호버 중인 챔피언이라도 쓴다. 없으면 0. */
function pickedChampionId(player: LcuPlayer): number {
  return player.championId || player.championPickIntent || 0;
}

function toSlot(player: LcuPlayer): LineupSlot {
  const assigned = player.assignedPosition?.toLowerCase() ?? '';
  return {
    cellId: player.cellId ?? -1,
    championId: pickedChampionId(player),
    championName: null, // 밴픽 API는 숫자 id만 준다
    position: POSITION_BY_LCU[assigned] ?? null,
  };
}

/**
 * 밴픽 세션을 렌더러가 쓸 형태로 정리한다.
 *
 * 적팀의 assignedPosition은 LCU가 보통 빈 문자열로 준다. 백엔드가
 * 아군·적군의 position은 쓰지 않고 이름만 보므로 null이어도 문제없다.
 */
export function parseChampSelect(raw: unknown): Lineup | null {
  const session = raw as LcuSession;
  if (!session || !Array.isArray(session.myTeam)) return null;

  const myCellId = session.localPlayerCellId ?? -1;
  const me = session.myTeam.find((player) => player.cellId === myCellId);

  return {
    sessionId: 0, // main.ts 가 실제 값으로 덮어쓴다
    source: 'champ-select',
    myCellId,
    myChampionId: me ? pickedChampionId(me) : 0,
    myChampionName: null,
    myPosition: me ? toSlot(me).position : null,
    myItemIds: [], // 밴픽 단계엔 상점이 없다
    allies: session.myTeam.map(toSlot),
    enemies: (session.theirTeam ?? []).map(toSlot),
  };
}
