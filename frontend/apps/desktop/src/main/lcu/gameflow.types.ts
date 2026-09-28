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
  localPlayer: { championId: number };
  teams: { teamId: number; isPlayerTeam: boolean; isWinningTeam: boolean }[];
}
