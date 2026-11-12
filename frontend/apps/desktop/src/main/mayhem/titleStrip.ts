import { toMask, type Gray, type Mask, type Soft } from './titleMatch';

const SCREEN_THRESHOLD = 150; // 이보다 밝으면 글자 (제목 줄 찾기)
// 마스크를 만들 때는 더 어두운 획까지 글자로 본다. 작은 화면에서 얇은 세로획은 150 아래로 흐려진다
const MASK_THRESHOLD = 110;
const MASK_SOFT: Soft = { low: 60, high: 180 };
const MIN_ROW_PIXELS = 2; // 한 줄에 글자 픽셀이 이만큼은 있어야 글자 줄
const SIDE_CROP = 0.04; // 양옆 카드 테두리 제외

// 아래는 모두 카드 높이에 대한 비율
const ROW_GAP = 0.006; // 빈 줄이 이보다 길면 다른 묶음
const MIN_LINE = 0.01; // 이보다 얇은 묶음은 잡티
const MIN_TITLE = 0.022; // 제목 글자 높이 범위
const MAX_TITLE = 0.05;

/** 글자 줄 묶음의 위·아래 줄 번호 */
type Rows = { top: number; bottom: number };

/**
 * 캡처한 컬러 비트맵(픽셀당 B, G, R, A)을 밝기 하나짜리 흑백 그림으로 바꾼다.
 * 사람 눈이 느끼는 밝기 공식(0.299R + 0.587G + 0.114B)을 쓴다.
 */
export function toLuma(bitmap: Uint8Array, width: number, height: number): Gray {
  const data = new Uint8Array(width * height);
  for (let i = 0; i < data.length; i++) {
    const p = i * 4; // B, G, R, A
    data[i] = Math.round(0.114 * bitmap[p] + 0.587 * bitmap[p + 1] + 0.299 * bitmap[p + 2]);
  }
  return { width, height, data };
}

/**
 * 제목 영역을 위에서부터 훑어 글자가 있는 줄들을 묶음으로 모으고,
 * 잡티를 건너뛴 첫 묶음이 제목 높이면 그 위·아래 줄을 돌려준다. 아니면 null.
 */
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

/**
 * 제목 줄만 위아래 한 줄씩 여유를 두고 잘라낸다.
 * 제목이 그림 맨 위나 맨 아래에 닿아 있으면 잘린 것으로 보고 null.
 */
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

/**
 * 화면에서 찍은 제목 영역을 비교용 마스크(높이 32)로 만든다.
 * 제목을 못 찾았거나 잘렸으면 null.
 */
export function titleMask(image: Gray, cardHeight: number): Mask | null {
  const title = cutTitle(image, cardHeight);
  return (
    title &&
    toMask(title, MASK_THRESHOLD, { left: SIDE_CROP, right: SIDE_CROP, bottom: 0 }, MASK_SOFT)
  );
}
