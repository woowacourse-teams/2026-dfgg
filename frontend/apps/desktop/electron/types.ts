/**
 * IPC 경계에서 오가는 타입. 메인 프로세스가 소유하고 렌더러는 타입만 가져다 쓴다.
 *
 * tsconfig.electron.json의 rootDir이 이 폴더라 packages/ 를 import할 수 없다.
 * Position은 packages/shared/types.ts의 것과 같은 값을 유지해야 한다.
 */
export type Position = 'TOP' | 'JUNGLE' | 'MID' | 'BOTTOM' | 'SUPPORT';

/**
 * 한 자리. 밴픽에서는 championId로, 인게임에서는 championName으로 온다.
 * 아직 안 고른 자리는 championId가 0이고 championName이 null이다.
 */
export interface LineupSlot {
  /** 팀 내 자리 번호. 나를 골라내는 기준이다. 인게임·적팀은 -1로 온다. */
  cellId: number;
  championId: number;
  /** 인게임 API가 주는 한글명. 밴픽에서는 null. */
  championName: string | null;
  position: Position | null;
}

export interface Lineup {
  /**
   * 한 판을 식별하는 번호. 밴픽부터 게임 종료까지 같은 값을 유지한다.
   * 렌더러는 이 값이 바뀔 때만 추천을 다시 요청한다.
   */
  sessionId: number;
  /** 어디서 읽었는지. 화면 문구를 다르게 하는 데 쓴다. */
  source: 'champ-select' | 'in-game';
  /** 내 자리의 cellId. 아군 배열에서 나를 골라내는 데 쓴다. 인게임은 -1. */
  myCellId: number;
  myChampionId: number;
  myChampionName: string | null;
  myPosition: Position | null;
  /**
   * 지금 내가 들고 있는 완성 아이템 id 목록. 밴픽 단계엔 상점이 없어 항상
   * 빈 배열이고, 인게임에서만 채워진다. 추천 빌드에서 이미 산 아이템에
   * 체크 표시를 하는 데 쓴다.
   */
  myItemIds: number[];
  allies: LineupSlot[];
  enemies: LineupSlot[];
}

export type LcuStatus = 'connected' | 'disconnected';
