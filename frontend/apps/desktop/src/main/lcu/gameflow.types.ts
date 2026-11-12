/** /lol-gameflow/v1/session 에서 쓰는 필드만 적었다. */
export interface LcuGameflowSession {
  gameData: {
    gameId: number;
    /** 사용자 설정 게임(연습 포함)이면 true. 이때 queue 는 비어 있을 수 있다 */
    isCustomGame?: boolean;
    queue: { id: number; gameMode: string };
  };
  /** 실제로 들어간 맵과 모드. 사용자 설정 게임은 queue 대신 여기서 모드를 본다 */
  map?: { id: number; gameMode: string };
}

/** /lol-end-of-game/v1/eog-stats-block 에서 쓰는 필드만 적었다. */
export interface LcuEndOfGameStats {
  gameId: number;
  // 응답 형식을 실제로 확인하지 못해서 없을 수도 있다고 본다.
  localPlayer?: { championId?: number };
  teams?: { teamId: number; isPlayerTeam: boolean; isWinningTeam: boolean }[];
}
