import { toMask, type Gray, type Mask } from './titleMatch';

const SCREEN_THRESHOLD = 150; // 이보다 밝으면 글자
const MIN_ROW_PIXELS = 2; // 한 줄에 글자 픽셀이 이만큼은 있어야 글자 줄
const SIDE_CROP = 0.04; // 양옆 카드 테두리 제외

// 아래는 모두 카드 높이에 대한 비율
const ROW_GAP = 0.006; // 빈 줄이 이보다 길면 다른 묶음
const MIN_LINE = 0.01; // 이보다 얇은 묶음은 잡티
const MIN_TITLE = 0.022; // 제목 글자 높이 범위
const MAX_TITLE = 0.05;

type Rows = { top: number; bottom: number };

export function toLuma(bitmap: Uint8Array, width: number, height: number): Gray {
  const data = new Uint8Array(width * height);
  for (let i = 0; i < data.length; i++) {
    const p = i * 4; // B, G, R, A
    data[i] = Math.round(0.114 * bitmap[p] + 0.587 * bitmap[p + 1] + 0.299 * bitmap[p + 2]);
  }
  return { width, height, data };
}

export function findTitleRows(image: Gray, cardHeight: number): Rows | null {
  const left = Math.floor(image.width * SIDE_CROP);
  const right = Math.ceil(image.width * (1 - SIDE_CROP));
  const gap = Math.max(2, Math.round(cardHeight * ROW_GAP));

  const groups: Rows[] = [];
  for (let y = 0; y < image.height; y++) {
    let count = 0;
    for (let x = left; x < right; x++) {
      if (image.data[y * image.width + x] > SCREEN_THRESHOLD) count++;
    }
    if (count < MIN_ROW_PIXELS) continue;

    const last = groups.at(-1);
    if (last && y - last.bottom <= gap) last.bottom = y;
    else groups.push({ top: y, bottom: y });
  }

  const ratio = (g: Rows) => (g.bottom - g.top + 1) / cardHeight;
  const first = groups.find((g) => ratio(g) >= MIN_LINE);
  if (!first || ratio(first) < MIN_TITLE || ratio(first) > MAX_TITLE) return null;
  return first;
}

export function cutTitle(image: Gray, cardHeight: number): Gray | null {
  const rows = findTitleRows(image, cardHeight);
  if (!rows) return null;
  if (rows.top === 0 || rows.bottom === image.height - 1) return null; // 위나 아래가 잘림

  const top = rows.top - 1;
  const bottom = rows.bottom + 1;
  return {
    width: image.width,
    height: bottom - top + 1,
    data: image.data.slice(top * image.width, (bottom + 1) * image.width),
  };
}

export function titleMask(image: Gray, cardHeight: number): Mask | null {
  const title = cutTitle(image, cardHeight);
  return title && toMask(title, SCREEN_THRESHOLD, { left: SIDE_CROP, right: SIDE_CROP, bottom: 0 });
}
