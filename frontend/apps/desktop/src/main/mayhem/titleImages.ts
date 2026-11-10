import { nativeImage } from 'electron';
import type { TitleImage } from '../api/augments';
import { toMask, type Gray, type Mask } from './titleMatch';

const TITLE_THRESHOLD = 128;
const MAX_WIDTH = 4000;
const MAX_HEIGHT = 200;

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
