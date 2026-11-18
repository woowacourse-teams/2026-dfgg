/** 비교할 때 쓰는 그림 크기. 아이콘 모양을 알아보기엔 충분하고 계산은 가볍다 */
export const VECTOR_SIZE = 48;
/** 카드 안쪽 배경색. 투명한 아이콘을 이 위에 얹어 화면과 같은 조건으로 만든다 */
const CARD_BACKGROUND = [5, 14, 16];

/**
 * BGRA 비트맵(VECTOR_SIZE × VECTOR_SIZE)을 비교용 벡터로 바꾼다.
 * 평균을 빼고 길이를 1 로 맞춰서, 밝기가 조금 달라도 모양이 같으면 가깝게 나온다.
 */
export function toVector(bitmap: Uint8Array, withAlpha: boolean): Float32Array {
  const pixels = VECTOR_SIZE * VECTOR_SIZE;
  const vector = new Float32Array(pixels * 3);

  for (let i = 0; i < pixels; i++) {
    const alpha = withAlpha ? bitmap[i * 4 + 3] / 255 : 1;
    const rgb = [bitmap[i * 4 + 2], bitmap[i * 4 + 1], bitmap[i * 4]]; // BGRA → RGB
    for (let c = 0; c < 3; c++) {
      vector[i * 3 + c] = rgb[c] * alpha + CARD_BACKGROUND[c] * (1 - alpha);
    }
  }

  let sum = 0;
  for (const v of vector) sum += v;
  const mean = sum / vector.length;

  let squares = 0;
  for (let i = 0; i < vector.length; i++) {
    vector[i] -= mean;
    squares += vector[i] ** 2;
  }

  const length = Math.sqrt(squares) || 1;
  for (let i = 0; i < vector.length; i++) vector[i] /= length;
  return vector;
}

/** 두 벡터가 얼마나 닮았는지. 1 이면 같은 그림, 0 근처면 관계없는 그림 */
export function similarity(a: Float32Array, b: Float32Array): number {
  let sum = 0;
  for (let i = 0; i < a.length; i++) sum += a[i] * b[i];
  return sum;
}

/** 1등이 이 점수보다 낮으면 어떤 증강과도 안 닮았다고 본다 */
export const MIN_SIMILARITY = 0.75;
/** 1등이 2등보다 이만큼은 앞서야 고른다 */
export const MIN_LEAD = 0.03;

export type Scored<T> = { candidate: T; score: number };
export type PickResult<T> =
  | { kind: 'picked'; picked: T; top: Scored<T>[] }
  | { kind: 'low' | 'same-icon' | 'close' | 'empty'; picked: null; top: Scored<T>[] };

/**
 * 카드 벡터와 후보 벡터들을 비교해 하나를 고른다. 확실하지 않으면 picked 가 null 이다.
 * 틀린 픽률을 보여 주는 것보다 아무것도 안 보여 주는 게 낫다.
 */
export function pickBest<T extends { iconUrl: string }>(
  card: Float32Array,
  candidates: { candidate: T; vector: Float32Array }[],
): PickResult<T> {
  const scored = candidates
    .map(({ candidate, vector }) => ({ candidate, score: similarity(card, vector) }))
    .sort((a, b) => b.score - a.score);
  const top = scored.slice(0, 3);
  const [first, second] = scored;

  if (!first) return { kind: 'empty', picked: null, top };
  if (first.score < MIN_SIMILARITY) return { kind: 'low', picked: null, top };
  if (second && second.candidate.iconUrl === first.candidate.iconUrl) {
    return { kind: 'same-icon', picked: null, top };
  }
  if (second && first.score - second.score < MIN_LEAD) {
    return { kind: 'close', picked: null, top };
  }
  return { kind: 'picked', picked: first.candidate, top };
}
