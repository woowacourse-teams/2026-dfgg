import type { MayhemAugment } from '../../shared/types';
import type { Candidate } from './titleImages';
import { recordKind } from './mayhemStats';
import { pickByTitle, type Mask } from './titleMatch';

/** 제목 마스크 하나를 후보와 비교해 판정한다. 확실하지 않으면 null */
export function judgeTitle(mask: Mask, candidates: Candidate[]): MayhemAugment | null {
  const result = pickByTitle(mask, candidates);
  recordKind(result.kind);
  console.log(
    '[mayhem] 판정',
    result.kind,
    result.top.slice(0, 2).map(({ candidate, score }) => `${candidate.name} ${score.toFixed(3)}`),
  );
  return result.picked;
}
