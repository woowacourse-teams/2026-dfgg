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
