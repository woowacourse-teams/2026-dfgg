/** DF 스코어 등수를 부르는 이름. 1등 MVP, 2·3등 TOP 2·3, 4등부터는 그냥 'N등'. */
export function rankTitle(rank: number): string {
  if (rank === 1) return 'MVP';
  if (rank <= 3) return `TOP ${rank}`;
  return `${rank}등`;
}

/** 등수 색. 1등 금색, 2·3등 브랜드 레드, 나머지는 무채색. */
export type RankTone = 'gold' | 'top' | 'plain';

export function rankTone(rank: number): RankTone {
  if (rank === 1) return 'gold';
  if (rank <= 3) return 'top';
  return 'plain';
}
