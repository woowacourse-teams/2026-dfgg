import type { MayhemAugment } from '../../shared/types';
import type { Candidate } from './titleImages';
import { pickByTitle, type Mask } from './titleMatch';

/** 픽셀 평균 차이가 이보다 작으면 같은 제목으로 본다 (0~1) */
const SAME_DIFF = 0.08;

/** 카드 한 칸의 판정 결과. 다음 판정 때 같은 제목인지 비교하려고 마스크도 들고 있다 */
export type Slot = { mask: Mask; augment: MayhemAugment | null };

/** 두 마스크가 같은 제목인지. 폭이 다르면 다른 제목으로 본다 */
export function sameMask(a: Mask, b: Mask): boolean {
  if (a.width !== b.width || a.height !== b.height) return false;
  let diff = 0;
  for (let i = 0; i < a.data.length; i++) diff += Math.abs(a.data[i] - b.data[i]);
  return diff / a.data.length < SAME_DIFF;
}

/**
 * 카드 한 장을 판정한다. 이전 판정과 제목이 같으면 다시 비교하지 않고 그 결과를 쓴다.
 * 확실하지 않으면 augment 가 null.
 */
export function judgeCard(mask: Mask, candidates: Candidate[], previous: Slot | null): Slot {
  if (previous && sameMask(previous.mask, mask)) return previous;

  const result = pickByTitle(mask, candidates);
  console.log(
    '[mayhem] 판정',
    result.kind,
    result.top.slice(0, 2).map(({ candidate, score }) => `${candidate.name} ${score.toFixed(3)}`),
  );
  return { mask, augment: result.picked };
}
