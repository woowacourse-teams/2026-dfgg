/** /lol-gameflow/v1/session 에서 쓰는 필드만 적었다. */
export interface LcuGameflowSession {
  gameData: {
    gameId: number;
    queue: { id: number };
  };
}

/** /lol-end-of-game/v1/eog-stats-block 에서 쓰는 필드만 적었다. */
export interface LcuEndOfGameStats {
  gameId: number;
  // 응답 형식을 실제로 확인하지 못해서 없을 수도 있다고 본다.
  localPlayer?: { championId?: number };
  teams?: { teamId: number; isPlayerTeam: boolean; isWinningTeam: boolean }[];
}
