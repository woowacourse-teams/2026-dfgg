import { describe, expect, it } from 'vitest';

import { similarity, toVector, VECTOR_SIZE } from '../vector';

/** 픽셀마다 색을 정하는 함수로 BGRA 비트맵을 만든다 */
function bitmap(color: (x: number, y: number) => [number, number, number, number]) {
  const data = new Uint8Array(VECTOR_SIZE * VECTOR_SIZE * 4);
  for (let y = 0; y < VECTOR_SIZE; y++) {
    for (let x = 0; x < VECTOR_SIZE; x++) {
      const [r, g, b, a] = color(x, y);
      data.set([b, g, r, a], (y * VECTOR_SIZE + x) * 4);
    }
  }
  return data;
}

const circle = (shift = 0) =>
  bitmap((x, y) => {
    const inside = (x - 24) ** 2 + (y - 24) ** 2 < 15 ** 2;
    return inside ? [200 + shift, 80 + shift, 40 + shift, 255] : [5, 14, 16, 255];
  });

const stripes = bitmap((x) => (x % 8 < 4 ? [40, 200, 80, 255] : [5, 14, 16, 255]));

describe('toVector / similarity', () => {
  it('같은 그림은 1 에 가깝다', () => {
    expect(similarity(toVector(circle(), false), toVector(circle(), false))).toBeCloseTo(1, 5);
  });

  it('전체 밝기만 달라도 같은 그림으로 본다', () => {
    const score = similarity(toVector(circle(), false), toVector(circle(-30), false));
    expect(score).toBeGreaterThan(0.95);
  });

  it('모양이 다르면 점수가 낮다', () => {
    const score = similarity(toVector(circle(), false), toVector(stripes, false));
    expect(score).toBeLessThan(0.5);
  });

  it('투명한 바깥은 카드 배경색으로 채워 화면 쪽과 맞춘다', () => {
    // 정답 아이콘: 바깥이 투명
    const transparent = bitmap((x, y) =>
      (x - 24) ** 2 + (y - 24) ** 2 < 15 ** 2 ? [200, 80, 40, 255] : [255, 255, 255, 0],
    );
    const score = similarity(toVector(transparent, true), toVector(circle(), false));
    expect(score).toBeGreaterThan(0.99);
  });
});
