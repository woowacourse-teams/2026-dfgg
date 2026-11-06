import { nativeImage } from 'electron';

/**
 * OCR 로 이름이 둘 이상과 비슷하게 읽혔을 때만 쓴다.
 * 카드의 아이콘 부분과 후보 증강 아이콘을 작게 줄여 얼마나 닮았는지 비교한다.
 */

const SIZE = 48;
/** 카드 안쪽 배경색. 투명한 아이콘을 이 위에 얹어 화면과 같은 조건으로 비교한다 */
const CARD_BACKGROUND = [5, 14, 16];
/** 이만큼 닮고, 다음 후보보다 이만큼 앞서야 고른다 */
const MIN_SIMILARITY = 0.75;
const MIN_LEAD = 0.03;

const iconCache = new Map<string, Promise<Float32Array | null>>();

/** 평균을 빼고 길이를 1 로 맞춘 RGB 벡터. 밝기가 달라도 모양이 같으면 가깝게 나온다 */
function toVector(bitmap: Buffer, withAlpha: boolean): Float32Array {
  const vector = new Float32Array(SIZE * SIZE * 3);
  for (let i = 0; i < SIZE * SIZE; i++) {
    // nativeImage 비트맵은 BGRA
    const alpha = withAlpha ? bitmap[i * 4 + 3] / 255 : 1;
    const rgb = [bitmap[i * 4 + 2], bitmap[i * 4 + 1], bitmap[i * 4]];
    for (let c = 0; c < 3; c++) {
      vector[i * 3 + c] = rgb[c] * alpha + CARD_BACKGROUND[c] * (1 - alpha);
    }
  }
  const avg = vector.reduce((sum, v) => sum + v, 0) / vector.length;
  let norm = 0;
  for (let i = 0; i < vector.length; i++) {
    vector[i] -= avg;
    norm += vector[i] ** 2;
  }
  const length = Math.sqrt(norm) || 1;
  return vector.map((v) => v / length);
}

function vectorOf(png: Buffer, withAlpha: boolean) {
  const image = nativeImage.createFromBuffer(png);
  if (image.isEmpty()) return null;
  return toVector(
    image.resize({ width: SIZE, height: SIZE, quality: 'good' }).toBitmap(),
    withAlpha,
  );
}

function iconVector(url: string) {
  let cached = iconCache.get(url);
  if (!cached) {
    cached = fetch(url, { signal: AbortSignal.timeout(5000) })
      .then(async (response) =>
        response.ok ? vectorOf(Buffer.from(await response.arrayBuffer()), true) : null,
      )
      .catch(() => null);
    iconCache.set(url, cached);
  }
  return cached;
}

const dot = (a: Float32Array, b: Float32Array) => a.reduce((sum, v, i) => sum + v * b[i], 0);

/** 후보 중 카드 아이콘과 가장 닮은 것. 확실하지 않으면 null */
export async function pickByIcon<T extends { iconUrl: string }>(
  cardIconPng: Buffer,
  candidates: T[],
): Promise<T | null> {
  const card = vectorOf(cardIconPng, false);
  if (!card) return null;

  const scored = await Promise.all(
    candidates.map(async (candidate) => {
      const icon = await iconVector(candidate.iconUrl);
      return { candidate, score: icon ? dot(card, icon) : -1 };
    }),
  );
  scored.sort((a, b) => b.score - a.score);
  const [first, second] = scored;
  if (!first || first.score < MIN_SIMILARITY) return null;
  if (second && first.score - second.score < MIN_LEAD) return null;
  return first.candidate;
}
