import { nativeImage } from 'electron';
import { fetchAugmentTitles, type TitleImage } from '../api/augments';
import { toMask, type Gray, type Mask } from './titleMatch';
import type { MayhemAugment } from '../../shared/types';

const TITLE_THRESHOLD = 128; // 견본 그림에서 이보다 밝으면 글자
const MAX_WIDTH = 4000; // 견본 그림 크기 상한 (잘못된 데이터 거르기)
const MAX_HEIGHT = 200;
const TITLE_LANG = 'ko_KR'; // T6에서 게임 클라이언트 언어로 바꿈

/** 비교 후보 하나: 증강 정보와 그 증강의 제목 마스크 */
export type Candidate = { candidate: MayhemAugment; mask: Mask };

/** 패치·언어별로 한 번 만든 견본 마스크. 판이 바뀌어도 유지한다 */
let titleCache: { key: string; masks: Map<number, Mask> } | null = null;

/**
 * 흑백 PNG를 푼 비트맵(픽셀당 4바이트)에서 픽셀마다 첫 바이트만 꺼내 흑백 그림으로 만든다.
 * 흑백이라 색 세 값이 같아서 첫 바이트만 읽으면 된다.
 */
export function bitmapToGray(bitmap: Uint8Array, width: number, height: number): Gray {
  const data = new Uint8Array(width * height);
  for (let i = 0; i < data.length; i++) data[i] = bitmap[i * 4];
  return { width, height, data };
}

/**
 * 백엔드가 준 견본 하나(base64 PNG)를 풀어 흑백 그림으로 만든다.
 * 그림이 깨졌거나, 응답에 적힌 크기와 다르거나, 너무 크면 null.
 */
export function decodeTitle(image: TitleImage): Gray | null {
  if (!Number.isInteger(image.augmentId) || typeof image.png !== 'string') return null;

  const decoded = nativeImage.createFromBuffer(Buffer.from(image.png, 'base64'));
  if (decoded.isEmpty()) return null;

  const { width, height } = decoded.getSize();
  if (width !== image.width || height !== image.height) return null;
  if (width > MAX_WIDTH || height > MAX_HEIGHT) return null;

  return bitmapToGray(decoded.toBitmap(), width, height);
}

/**
 * 견본 그림 전체를 비교용 마스크로 바꿔 augmentId별로 담는다.
 * 풀 수 없거나 글자가 없는 견본은 빠진다.
 */
export function titleMasks(images: TitleImage[]): Map<number, Mask> {
  const masks = new Map<number, Mask>();
  for (const image of images) {
    const gray = decodeTitle(image);
    const mask = gray && toMask(gray, TITLE_THRESHOLD);
    if (mask) masks.set(image.augmentId, mask);
  }
  return masks;
}

/**
 * 패치·언어에 맞는 견본 마스크를 돌려준다.
 * 이미 만든 적이 있으면 캐시를 쓰고, 없으면 백엔드에서 받아 만든다.
 */
async function loadTitleMasks(patch: string): Promise<Map<number, Mask>> {
  const key = `${patch}:${TITLE_LANG}`;
  if (titleCache?.key === key) return titleCache.masks;

  const titles = await fetchAugmentTitles(patch);
  const masks = titleMasks(titles.images);
  titleCache = { key, masks };
  console.info(`[mayhem] 제목 견본 ${masks.size}/${titles.images.length} (${titles.font})`);
  return masks;
}

/**
 * 이 챔피언의 증강 목록과 견본 마스크를 augmentId로 짝지어 비교 후보를 만든다.
 * 견본이 없는 증강은 후보에서 빠진다.
 */
export async function loadCandidates(list: MayhemAugment[], patch: string): Promise<Candidate[]> {
  const masks = await loadTitleMasks(patch);
  const candidates = list.flatMap((candidate) => {
    const mask = masks.get(candidate.augmentId);
    return mask ? [{ candidate, mask }] : [];
  });
  console.info(`[mayhem] 후보 ${candidates.length}/${list.length}`);
  return candidates;
}
