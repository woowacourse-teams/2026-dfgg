import { describe, expect, it } from 'vitest';

import { type Box, detectCards, fingerprint, type Frame, isSameCard } from './detect';

/**
 * 실제 화면 대신 증강 선택 화면을 흉내 낸 프레임을 그린다.
 * 게임 배경은 들쭉날쭉한 중간 밝기, 카드는 밝은 테두리와 어두운 안쪽, 가운데에 제목·아이콘·설명 글.
 */
function makeFrame(width: number, height: number, scale = 1, withCards = 3): Frame {
  const data = new Uint8ClampedArray(width * height * 4);
  let seed = 7;
  const random = () => ((seed = (seed * 16807) % 2147483647) / 2147483647) * 255;

  // 32px 칸마다 다른 색. 맵·미니언·스킬 효과처럼 밝은 칸도 섞인다
  const block = Math.max(8, Math.round(height / 34));
  for (let by = 0; by < height; by += block) {
    for (let bx = 0; bx < width; bx += block) {
      const base = random() > 0.9 ? 200 : 30 + random() * 70;
      fill(data, width, height, { x: bx, y: by, width: block, height: block }, [
        base * 0.6,
        base * 0.8,
        base,
      ]);
    }
  }

  const cardWidth = 0.299 * height * scale;
  const cardHeight = 0.494 * height * scale;
  const pitch = 0.3338 * height * scale;
  const centerY = (0.1615 + 0.247) * height;
  for (let slot = 0; slot < withCards; slot++) {
    const card = {
      x: width / 2 + (slot - 1) * pitch - cardWidth / 2,
      y: centerY - cardHeight / 2,
      width: cardWidth,
      height: cardHeight,
    };
    fill(data, width, height, card, [220, 190, 150]); // 금색 테두리
    fill(data, width, height, part(card, 0.045, 0.03, 0.91, 0.94), [5, 15, 17]); // 어두운 안쪽
    fill(data, width, height, part(card, 0.3, 0.1, 0.4, 0.25), [230, 210, 170]); // 아이콘
    fill(data, width, height, part(card, 0.3 + slot * 0.05, 0.46, 0.3, 0.04), [240, 240, 240]); // 제목
    for (const line of [0.6, 0.65, 0.7]) {
      fill(data, width, height, part(card, 0.22, line, 0.56, 0.02), [200, 200, 200]); // 설명
    }
  }
  return { width, height, data };
}

function part(card: Box, x: number, y: number, w: number, h: number): Box {
  return {
    x: card.x + card.width * x,
    y: card.y + card.height * y,
    width: card.width * w,
    height: card.height * h,
  };
}

function fill(data: Uint8ClampedArray, width: number, height: number, box: Box, rgb: number[]) {
  const x0 = Math.max(0, Math.round(box.x));
  const y0 = Math.max(0, Math.round(box.y));
  const x1 = Math.min(width, Math.round(box.x + box.width));
  const y1 = Math.min(height, Math.round(box.y + box.height));
  for (let y = y0; y < y1; y++) {
    for (let x = x0; x < x1; x++) {
      const i = (y * width + x) * 4;
      data[i] = rgb[0];
      data[i + 1] = rgb[1];
      data[i + 2] = rgb[2];
      data[i + 3] = 255;
    }
  }
}

/** 카드 왼쪽 위가 기대 자리에서 카드 폭의 5% 안에 있어야 한다 */
function expectCards(frame: Frame, scale: number, boxes: Box[] | null) {
  expect(boxes).not.toBeNull();
  const expectedWidth = 0.299 * frame.height * scale;
  boxes!.forEach((box, slot) => {
    const expectedX =
      frame.width / 2 + (slot - 1) * 0.3338 * frame.height * scale - expectedWidth / 2;
    expect(Math.abs(box.x - expectedX)).toBeLessThan(expectedWidth * 0.05);
    expect(Math.abs(box.width - expectedWidth)).toBeLessThan(expectedWidth * 0.05);
  });
}

describe('증강 카드 찾기', () => {
  const screens = [
    [1280, 720, '720p'],
    [1920, 1080, '1080p'],
    [2560, 1440, '1440p'],
    [3840, 2160, '4K'],
    [1920, 1200, '16:10'],
    [3440, 1440, '21:9 울트라와이드'],
    [1440, 1080, '4:3'],
  ] as const;

  it.each(screens)('%ix%i (%s) 화면에서 세 장을 찾는다', (width, height) => {
    const frame = makeFrame(width, height);
    expectCards(frame, 1, detectCards(frame));
  });

  it.each([0.85, 1.15])('HUD 크기 설정으로 카드가 %f 배여도 찾는다', (scale) => {
    const frame = makeFrame(1920, 1080, scale);
    expectCards(frame, scale, detectCards(frame));
  });

  it('선택 창이 없으면 찾지 않는다', () => {
    expect(detectCards(makeFrame(1920, 1080, 1, 0))).toBeNull();
  });

  it('카드가 세 장이 아니면 찾지 않는다', () => {
    expect(detectCards(makeFrame(1920, 1080, 1, 2))).toBeNull();
  });
});

describe('카드 바뀜 확인', () => {
  it('같은 화면은 같은 카드, 제목이 바뀌면 다른 카드다', () => {
    const before = makeFrame(1920, 1080);
    const boxes = detectCards(before)!;
    const after = makeFrame(1920, 1080);
    // 가운데 카드만 리롤한 것처럼 제목 자리를 지운다
    fill(after.data, 1920, 1080, part(boxes[1], 0.06, 0.445, 0.88, 0.07), [5, 15, 17]);

    expect(isSameCard(fingerprint(before, boxes[0]), fingerprint(after, boxes[0]))).toBe(true);
    expect(isSameCard(fingerprint(before, boxes[1]), fingerprint(after, boxes[1]))).toBe(false);
  });
});
