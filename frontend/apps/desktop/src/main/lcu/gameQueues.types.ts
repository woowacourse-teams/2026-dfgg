/** /lol-game-queues/v1/queues 원본. 88개쯤 오는데 id → 이름만 쓴다. */
export type LcuGameQueue = {
  id: number;
  /** 한글 이름. 예: '개인/2인 랭크 게임', '무작위 총력전: 아수라장' */
  name: string;
  shortName: string;
  gameMode: string;
};
