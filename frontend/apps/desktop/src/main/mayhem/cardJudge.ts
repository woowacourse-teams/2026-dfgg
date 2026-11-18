import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

import type { MayhemAugment } from '../../shared/types';
import type { Candidate } from './titleImages';
import { recordKind, recordMiss, recordTieBreak } from './mayhemStats';
import { pickByTitle, type Mask } from './titleMatch';

/** 제목 마스크 하나를 후보와 비교해 판정한다. 확실하지 않으면 null */
export function judgeTitle(mask: Mask, candidates: Candidate[]): MayhemAugment | null {
  const result = pickByTitle(mask, candidates);
  recordKind(result.kind);
  if (result.kind === 'picked' && result.tieBreak) recordTieBreak();
  console.log(
    '[mayhem] 판정',
    result.kind === 'picked' && result.tieBreak ? 'picked(재비교)' : result.kind,
    result.top.slice(0, 2).map(({ candidate, score }) => `${candidate.name} ${score.toFixed(3)}`),
  );
  if (result.kind === 'low' || result.kind === 'close') {
    recordMiss(result.kind, result.top, mask.width);
  }
  if (result.kind === 'close') dumpClose(mask, candidates, result.top);
  return result.picked;
}

/**
 * 실험용. DFGG_CAPTURE_TEST=1 일 때 헷갈린 판정의 화면 마스크와 1·2등 견본을 저장한다.
 * 재비교 기준을 실제 화면으로 맞춰 보는 데 쓴다.
 */
function dumpClose(
  mask: Mask,
  candidates: Candidate[],
  top: { candidate: MayhemAugment; score: number }[],
) {
  if (!process.env.DFGG_CAPTURE_TEST) return;
  try {
    const toJson = (m: Mask) => ({ width: m.width, height: m.height, data: Array.from(m.data) });
    const masks = top.slice(0, 2).flatMap(({ candidate, score }) => {
      const found = candidates.find((c) => c.candidate === candidate);
      return found ? [{ name: candidate.name, score, mask: toJson(found.mask) }] : [];
    });
    const dir = path.join(app.getPath('userData'), 'capture-test');
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(
      path.join(dir, `close-${Date.now()}.json`),
      JSON.stringify({ screen: toJson(mask), top: masks }),
    );
  } catch (error) {
    console.warn('[mayhem] 헷갈림 저장 실패', error);
  }
}
