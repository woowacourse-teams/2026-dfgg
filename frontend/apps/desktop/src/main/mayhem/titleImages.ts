import { nativeImage } from 'electron';
import { fetchAugmentTitles, type TitleImage } from '../api/augments';
import { toMask, type Gray, type Mask } from './titleMatch';
import { MayhemAugment } from '../../shared/types';

const TITLE_THRESHOLD = 128;
const MAX_WIDTH = 4000;
const MAX_HEIGHT = 200;
const TITLE_LANG = 'ko_KR';

export type Candidate = { candidate: MayhemAugment; mask: Mask };

let titleCache: { key: string; masks: Map<number, Mask> } | null = null;

export function bitmapToGray(bitmap: Uint8Array, width: number, height: number): Gray {
  const data = new Uint8Array(width * height);
  for (let i = 0; i < data.length; i++) data[i] = bitmap[i * 4];
  return { width, height, data };
}

export function decodeTitle(image: TitleImage): Gray | null {
  if (!Number.isInteger(image.augmentId) || typeof image.png !== 'string') return null;

  const decoded = nativeImage.createFromBuffer(Buffer.from(image.png, 'base64'));
  if (decoded.isEmpty()) return null;

  const { width, height } = decoded.getSize();
  if (width !== image.width || height !== image.height) return null;
  if (width > MAX_WIDTH || height > MAX_HEIGHT) return null;

  return bitmapToGray(decoded.toBitmap(), width, height);
}

export function titleMasks(images: TitleImage[]): Map<number, Mask> {
  const masks = new Map<number, Mask>();
  for (const image of images) {
    const gray = decodeTitle(image);
    const mask = gray && toMask(gray, TITLE_THRESHOLD);
    if (mask) masks.set(image.augmentId, mask);
  }
  return masks;
}

async function loadTitleMasks(patch: string): Promise<Map<number, Mask>> {
  const key = `${patch}:${TITLE_LANG}`;
  if (titleCache?.key === key) return titleCache.masks;

  const titles = await fetchAugmentTitles(patch, TITLE_LANG);
  const masks = titleMasks(titles.images);
  titleCache = { key, masks };
  console.info(`[mayhem] 제목 견본 ${masks.size}/${titles.images.length} (${titles.font})`);
  return masks;
}

export async function loadCandidates(list: MayhemAugment[], patch: string): Promise<Candidate[]> {
  const masks = await loadTitleMasks(patch);
  const candidates = list.flatMap((candidate) => {
    const mask = masks.get(candidate.augmentId);
    return mask ? [{ candidate, mask }] : [];
  });
  console.info(`[mayhem] 후보 ${candidates.length}/${list.length}`);
  return candidates;
}
