import { describe, expect, it } from 'vitest';

import {
  type Gray,
  MASK_HEIGHT,
  MIN_TITLE_LEAD,
  MIN_TITLE_SCORE,
  pickByTitle,
  SCREEN_CROP,
  titleScore,
  toMask,
} from '../titleMatch';

/** 막대(글자 대신)를 그린 흑백 그림. bars = 각 막대의 [시작x, 폭, 높이비율] */
function drawBars(
  width: number,
  height: number,
  bars: [number, number, number][],
  ink = 230,
  paper = 10,
): Gray {
  const data = new Uint8Array(width * height).fill(paper);
  for (const [x0, w, ratio] of bars) {
    const top = Math.round(height * (1 - ratio) * 0.5);
    const bottom = Math.round(height - height * (1 - ratio) * 0.5);
    for (let y = top; y < bottom; y++) for (let x = x0; x < x0 + w; x++) data[y * width + x] = ink;
  }
  return { width, height, data };
}

const WORD_A: [number, number, number][] = [
  [10, 6, 1],
  [22, 6, 0.6],
  [34, 6, 1],
  [46, 6, 0.6],
];
const WORD_B: [number, number, number][] = [
  [10, 14, 0.6],
  [34, 6, 1],
  [46, 14, 1],
];

describe('toMask', () => {
  it('글자 부분만 잘라 높이를 맞춘다', () => {
    const mask = toMask(drawBars(120, 60, WORD_A), 128)!;
    expect(mask.height).toBe(MASK_HEIGHT);
    // 글자 영역 42×60 → 높이 32 에 맞추면 폭 약 22
    expect(mask.width).toBe(Math.round((42 * MASK_HEIGHT) / 60));
  });

  it('글자가 없으면 null', () => {
    expect(toMask(drawBars(50, 20, []), 128)).toBeNull();
  });

  it('화면 제목의 양옆 테두리와 아래 태그는 버린다', () => {
    // 왼쪽 끝 테두리(폭 2) + 가운데 글자 + 아래쪽 태그
    const image = drawBars(200, 50, [
      [0, 2, 1],
      [80, 40, 0.4],
    ]);
    for (let x = 90; x < 110; x++) for (let y = 44; y < 50; y++) image.data[y * 200 + x] = 230;
    const withNoise = toMask(image, 150, SCREEN_CROP)!;
    const clean = toMask(drawBars(200, 50, [[80, 40, 0.4]]), 150)!;
    expect(withNoise.width).toBe(clean.width);
  });
});

describe('titleScore', () => {
  it('같은 모양이면 크기가 달라도 1 에 가깝다', () => {
    const screen = toMask(drawBars(120, 60, WORD_A), 128)!;
    const candidate = toMask(
      drawBars(
        240,
        120,
        WORD_A.map(([x, w, r]) => [x * 2, w * 2, r]),
      ),
      128,
    )!;
    expect(titleScore(screen, candidate)).toBeGreaterThan(0.9);
  });

  it('모양이 다르면 낮다', () => {
    const screen = toMask(drawBars(120, 60, WORD_A), 128)!;
    const other = toMask(drawBars(120, 60, WORD_B), 128)!;
    expect(titleScore(screen, other)).toBeLessThan(0.5);
  });

  it('폭이 많이 다르면 깎인다', () => {
    const screen = toMask(drawBars(120, 60, WORD_A), 128)!;
    const wide = toMask(
      drawBars(
        240,
        60,
        WORD_A.map(([x, w, r]) => [x * 2, w * 2, r]),
      ),
      128,
    )!;
    expect(titleScore(screen, wide)).toBeLessThan(0.5);
  });
});

describe('pickByTitle', () => {
  const screen = toMask(drawBars(120, 60, WORD_A), 128)!;
  const a = { candidate: 'A', mask: toMask(drawBars(120, 60, WORD_A), 128)! };
  const b = { candidate: 'B', mask: toMask(drawBars(120, 60, WORD_B), 128)! };

  it('확실히 닮은 하나를 고른다', () => {
    const result = pickByTitle(screen, [b, a]);
    expect(result.kind).toBe('picked');
    expect(result.picked).toBe('A');
  });

  it('닮은 후보가 없으면 low', () => {
    const result = pickByTitle(screen, [b]);
    expect(result.top[0].score).toBeLessThan(MIN_TITLE_SCORE);
    expect(result.kind).toBe('low');
  });

  it('1등과 2등이 너무 가까우면 close', () => {
    const twin = { candidate: 'A2', mask: a.mask };
    const result = pickByTitle(screen, [a, twin]);
    expect(result.top[0].score - result.top[1].score).toBeLessThan(MIN_TITLE_LEAD);
    expect(result.kind).toBe('close');
  });

  it('후보가 없으면 empty', () => {
    expect(pickByTitle(screen, []).kind).toBe('empty');
  });
});

describe('pickByTitle 헷갈림 재비교', () => {
  // 뒤쪽 막대 6개는 같고 앞쪽 4개만 다른 두 제목 ("위력 추구"와 "가속 추구" 같은 경우)
  const COMMON: [number, number, number][] = [
    [60, 4, 1],
    [68, 4, 0.7],
    [76, 4, 1],
    [84, 4, 0.7],
    [92, 4, 1],
    [100, 4, 0.7],
  ];
  const head = (high: number, low: number): [number, number, number][] => [
    [10, 4, high],
    [18, 4, low],
    [26, 4, high],
    [34, 4, low],
  ];
  const word = (high: number, low: number) =>
    toMask(drawBars(120, 60, [...head(high, low), ...COMMON]), 128)!;
  const a = { candidate: 'A', mask: word(1, 0.5) };
  const b = { candidate: 'B', mask: word(0.5, 1) };

  it('같은 부분 때문에 점수가 붙어도, 다른 부분이 닮은 쪽을 고른다', () => {
    const result = pickByTitle(word(0.8, 0.7), [b, a]);
    expect(result.top[0].score - result.top[1].score).toBeLessThan(MIN_TITLE_LEAD);
    expect(result.kind).toBe('picked');
    expect(result.picked).toBe('A');
    expect(result.kind === 'picked' && result.tieBreak).toBe(true);
  });

  it('다른 부분도 어느 쪽인지 애매하면 그대로 close', () => {
    expect(pickByTitle(word(0.75, 0.75), [a, b]).kind).toBe('close');
  });

  it('차이가 큰 경우는 재비교 없이 고른다', () => {
    const result = pickByTitle(word(0.9, 0.6), [a, b]);
    expect(result.kind === 'picked' && result.tieBreak).toBeFalsy();
    expect(result.picked).toBe('A');
  });
});
