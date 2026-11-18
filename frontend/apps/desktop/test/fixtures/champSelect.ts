import type {
  LcuChampSelectAction,
  LcuChampSelectPlayer,
  LcuChampSelectSession,
} from '../../src/main/lcu/champSelect.types';
import type {
  Counters,
  RecommendedRunePage,
  RuneEntry,
  RuneRecommendation,
  TodayPicks,
} from '../../src/shared/types';

export const ME = 2;

export function player(overrides: Partial<LcuChampSelectPlayer> = {}): LcuChampSelectPlayer {
  return { cellId: 0, championId: 0, assignedPosition: '', ...overrides };
}

export function action(overrides: Partial<LcuChampSelectAction> = {}): LcuChampSelectAction {
  return {
    id: 0,
    actorCellId: 0,
    championId: 0,
    completed: false,
    isInProgress: false,
    type: 'pick',
    ...overrides,
  };
}

/** 밴픽 세션. 기본값은 내가 미드에서 오리아나(61)를 고르는 중, 상대 정글 비에고(234) 공개, 밴 하나 */
export function champSelectSession(
  overrides: Partial<LcuChampSelectSession> = {},
): LcuChampSelectSession {
  return {
    localPlayerCellId: ME,
    myTeam: [
      player({ cellId: 0, championId: 64, assignedPosition: 'jungle' }),
      player({ cellId: ME, championId: 61, assignedPosition: 'middle' }),
    ],
    theirTeam: [player({ cellId: 5, championId: 234 })],
    actions: [
      [action({ id: 1, actorCellId: 0, championId: 157, completed: true, type: 'ban' })],
      [action({ id: 3, actorCellId: ME, championId: 61, isInProgress: true })],
    ],
    ...overrides,
  };
}

const rune = (id: number): RuneEntry => ({ id, name: null, imageUrl: null });

export const RUNE_PAGE: RecommendedRunePage = {
  rank: 1,
  primaryStyle: rune(8200),
  keystone: rune(8229),
  primaryRunes: [rune(8226), rune(8210), rune(8237)],
  subStyle: rune(8300),
  subRunes: [rune(8304), rune(8345)],
  statShards: [rune(5008), rune(5008), rune(5001)],
  evidence: { games: 300, pickRate: 0.5 },
};

export const RUNES: RuneRecommendation = { patch: '15.20', championGames: 300, pages: [RUNE_PAGE] };
export const COUNTERS: Counters = { patch: '15.20', myPosition: 'MID', enemies: [] };
export const TODAY_PICKS: TodayPicks = { tournament: null, position: 'MID', picks: [] };
