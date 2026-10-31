import type { Position } from '../../shared/types';
import type { LcuChampSelectSession } from '../lcu/champSelect.types';

/** 밴픽 세션에서 추천에 필요한 것만 추린 값 */
export interface ChampSelectInfo {
  position: Position | null;
  myChampionId: number | null;
  locked: boolean;
  /** 나를 뺀 아군 픽 */
  allies: number[];
  /** 상대가 공개한 픽 */
  enemies: number[];
  bans: number[];
}

const POSITIONS: Record<string, Position> = {
  top: 'TOP',
  jungle: 'JUNGLE',
  middle: 'MID',
  bottom: 'BOTTOM',
  utility: 'SUPPORT',
};

// API 상한. 넘기면 400 이 온다.
const MAX_ALLIES = 4;
const MAX_ENEMIES = 5;
const MAX_BANS = 10;

const picked = (ids: number[]) => ids.filter((id) => id > 0);

export function toChampSelectInfo(session: LcuChampSelectSession): ChampSelectInfo {
  const me = session.myTeam.find((player) => player.cellId === session.localPlayerCellId);
  const actions = session.actions.flat();

  const myPick = actions.find(
    (action) => action.type === 'pick' && action.actorCellId === session.localPlayerCellId,
  );
  // 드래프트는 밴이 행동 목록에 남는다. 행동이 없는 큐만 bans 칸을 본다.
  const banActions = actions.filter((action) => action.type === 'ban' && action.completed);
  const bans = banActions.length
    ? banActions.map((action) => action.championId)
    : [...(session.bans?.myTeamBans ?? []), ...(session.bans?.theirTeamBans ?? [])];

  return {
    position: POSITIONS[me?.assignedPosition ?? ''] ?? null,
    myChampionId: me && me.championId > 0 ? me.championId : null,
    locked: myPick?.completed ?? false,
    allies: picked(
      session.myTeam.filter((player) => player !== me).map((player) => player.championId),
    ).slice(0, MAX_ALLIES),
    enemies: picked(session.theirTeam.map((player) => player.championId)).slice(0, MAX_ENEMIES),
    bans: picked(bans).slice(0, MAX_BANS),
  };
}

export const sameIds = (a: number[] = [], b: number[] = []): boolean =>
  a.length === b.length && a.every((id, at) => id === b[at]);
