/**
 * OCR 로 읽은 증강 이름을 챔피언 증강 목록과 맞춘다.
 * OCR 은 한두 글자를 틀리기 쉬워서 정확히 같은 이름 대신 가장 비슷한 이름을 찾는다.
 */

/** 이만큼 비슷하면 그 증강으로 본다 (1 이 완전히 같음) */
const ACCEPT_SCORE = 0.75;
/** 이보다 덜 비슷하면 후보로도 보지 않는다 */
const MIN_SCORE = 0.5;
/** 1등과 2등이 이만큼 벌어져야 1등을 믿는다. 아니면 아이콘으로 가린다 */
const MARGIN = 0.15;

export type AugmentNames = { augmentId: number; names: string[] };
export type ScoredAugment = { augmentId: number; score: number };
export type MatchDecision =
  | { kind: 'match'; augmentId: number }
  | { kind: 'ambiguous'; augmentIds: number[] }
  | { kind: 'none' };

/** 띄어쓰기·문장부호·대소문자는 OCR 이 자주 틀리므로 빼고 비교한다 */
export const normalizeName = (text: string) =>
  text
    .normalize('NFC')
    .toLowerCase()
    .replace(/[\s'’"`.,:;!?·\-_|()[\]]/g, '');

function levenshtein(a: string, b: string): number {
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    const row = [i];
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      row.push(Math.min(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + cost));
    }
    prev = row;
  }
  return prev[b.length];
}

/** 0~1. 1 이면 정규화한 글자가 같다 */
export function similarity(a: string, b: string): number {
  const x = normalizeName(a);
  const y = normalizeName(b);
  if (!x || !y) return 0;
  return 1 - levenshtein(x, y) / Math.max(x.length, y.length);
}

/** 증강마다 이름(한/영) 중 가장 비슷한 점수로, 높은 순 */
export function rankAugments(text: string, augments: AugmentNames[]): ScoredAugment[] {
  return augments
    .map(({ augmentId, names }) => ({
      augmentId,
      score: Math.max(0, ...names.map((name) => similarity(text, name))),
    }))
    .sort((a, b) => b.score - a.score);
}

/** 확실하면 그 증강, 비슷한 후보가 여럿이면 후보들, 아무것도 아니면 none */
export function decideAugment(ranked: ScoredAugment[]): MatchDecision {
  const [first, second] = ranked;
  if (!first || first.score < MIN_SCORE) return { kind: 'none' };
  if (first.score >= ACCEPT_SCORE && (second?.score ?? 0) <= first.score - MARGIN) {
    return { kind: 'match', augmentId: first.augmentId };
  }
  const close = ranked.filter(
    (entry) => entry.score >= MIN_SCORE && entry.score >= first.score - MARGIN,
  );
  return { kind: 'ambiguous', augmentIds: close.map((entry) => entry.augmentId) };
}
