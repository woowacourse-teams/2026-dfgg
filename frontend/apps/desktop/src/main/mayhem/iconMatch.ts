import { nativeImage } from 'electron';
import { pickBest, PickResult, toVector, VECTOR_SIZE } from './vector';

const FETCH_TIMEOUT_MS = 5000;

/** iconUrl → 벡터. 패치가 바뀌기 전까지 같은 그림이라 판이 바뀌어도 그대로 쓴다 */
const iconCache = new Map<string, Promise<Float32Array | null>>();

/** PNG → 벡터. 그림이 깨져 있으면 null */
export function vectorOf(png: Buffer, withAlpha: boolean): Float32Array | null {
  const image = nativeImage.createFromBuffer(png);
  if (image.isEmpty()) return null;
  const small = image.resize({ width: VECTOR_SIZE, height: VECTOR_SIZE, quality: 'good' });
  return toVector(small.toBitmap(), withAlpha);
}

/** 정답 아이콘 벡터. 같은 URL 은 한 번만 받는다 */
export function iconVector(url: string): Promise<Float32Array | null> {
  const cached = iconCache.get(url);
  if (cached) return cached;

  const loading = fetch(url, { signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) })
    .then(async (response) =>
      response.ok ? vectorOf(Buffer.from(await response.arrayBuffer()), true) : null,
    )
    .catch(() => null);

  iconCache.set(url, loading);
  loading.then((vector) => {
    if (!vector) iconCache.delete(url);
  });
  return loading;
}

/** 판 시작 때 정답 아이콘을 전부 받아 둔다. 받은 개수를 돌려준다 */
export async function prepareIcons(icons: { iconUrl: string }[]): Promise<number> {
  const vectors = await Promise.all(icons.map((icon) => iconVector(icon.iconUrl)));
  return vectors.filter(Boolean).length;
}

/** 카드 아이콘 PNG 를 후보 증강들과 비교한다 */
export async function matchIcon<T extends { iconUrl: string }>(
  cardIconPng: Buffer,
  candidates: T[],
): Promise<PickResult<T>> {
  const card = vectorOf(cardIconPng, false);
  if (!card) return { kind: 'empty', picked: null, top: [] };

  const loaded = await Promise.all(
    candidates.map(async (candidate) => ({
      candidate,
      vector: await iconVector(candidate.iconUrl),
    })),
  );

  const usable = loaded.filter(
    (entry): entry is { candidate: T; vector: Float32Array } => entry.vector !== null,
  );
  return pickBest(card, usable);
}
