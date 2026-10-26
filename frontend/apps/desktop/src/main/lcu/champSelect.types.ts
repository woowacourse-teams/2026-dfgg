/** /lol-champ-select/v1/session 에서 쓰는 필드만 적었다. */
export interface LcuChampSelectPlayer {
  cellId: number;
  /** 고른 챔피언. 아직 없으면 0 */
  championId: number;
  /** top · jungle · middle · bottom · utility. 배정이 없는 큐는 빈 문자열 */
  assignedPosition: string;
}

export interface LcuChampSelectAction {
  id: number;
  actorCellId: number;
  championId: number;
  completed: boolean;
  /** 지금 이 사람 차례인지 */
  isInProgress: boolean;
  /** pick · ban · ten_bans_reveal 등 */
  type: string;
}

export interface LcuChampSelectSession {
  localPlayerCellId: number;
  myTeam: LcuChampSelectPlayer[];
  theirTeam: LcuChampSelectPlayer[];
  /** 차례별로 묶인 행동 목록 */
  actions: LcuChampSelectAction[][];
  bans?: { myTeamBans: number[]; theirTeamBans: number[] };
}

/** /lol-perks/v1/pages 항목에서 쓰는 필드만 */
export interface LcuPerkPage {
  id: number;
  name: string;
  isDeletable: boolean;
  /** 지금 클라이언트에서 고른 페이지 */
  current: boolean;
}

/** /lol-perks/v1/inventory 에서 쓰는 필드만 */
export interface LcuPerkInventory {
  ownedPageCount: number;
}

/** /lol-game-data/assets/v1/champion-summary.json 항목. alias 가 영문 이름(Jayce, MonkeyKing) */
export interface LcuChampionSummary {
  id: number;
  alias: string;
}
