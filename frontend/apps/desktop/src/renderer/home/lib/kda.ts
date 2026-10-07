import type { MatchStats } from '../../../shared/types';

type Kda = Pick<MatchStats, 'kills' | 'deaths' | 'assists'>;

/** 데스가 0이면 나눌 수 없다. 롤 클라이언트와 같이 'Perfect' 로 본다. */
export const kdaValue = ({ kills, deaths, assists }: Kda): number =>
  deaths === 0 ? Infinity : (kills + assists) / deaths;

/**
 * 평점을 색으로 나눈다. 좁은 폭에서는 숫자 크기로만 강약을 주기 어려워
 * 색이 10명을 훑을 때 눈을 멈추게 하는 유일한 장치가 된다.
 */
export function kdaGrade(ratio: number): string {
  if (ratio === Infinity || ratio >= 5) return 'kda-best';
  if (ratio >= 3) return 'kda-good';
  if (ratio < 1.5) return 'kda-bad';
  return '';
}

export const formatRatio = (ratio: number): string =>
  ratio === Infinity ? 'Perfect' : ratio.toFixed(2);
