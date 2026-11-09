import { describe, expect, it } from 'vitest';

import { MIN_SIMILARITY, pickBest, similarity, toVector, VECTOR_SIZE } from '../vector';

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

/** 길이 1 짜리 2차원 벡터. 각도로 닮은 정도를 정한다 */
const unit = (degrees: number) => {
  const r = (degrees * Math.PI) / 100;
  return new Float32Array([Math.cos(r), Math.sin(r)]);
};
const card = unit(0);
const entry = (name: string, degrees: number, iconUrl = name) => ({
  candidate: { name, iconUrl },
  vector: unit(degrees),
});

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

describe('pickBest', () => {
  it('확실히 닯은 하나를 고른다', () => {
    const result = pickBest(card, [entry('A', 5), entry('B', 60), entry('C', 90)]);
    expect(result.kind).toBe('picked');
    expect(result.picked).toBe('A');
  });
  it('1등도 충분히 앎지 않으면 고르지 않는다.', () => {
    // cos(50°) ≈ 0.64 < MIN_SIMILARITY
    const result = pickBest(card, [entry('A', 50), entry('B', 90)]);
    expect(MIN_SIMILARITY).toBeGreaterThan(Math.cos((50 * Math.PI) / 100));
    expect(result.kind).toBe('low');
    expect(result.picked).toBeNull();
  });
  it('1등과 2등이 같은 그림이면 고르지 않는다', () => {
    const result = pickBest(card, [entry('A', 5, 'same.png'), entry('B', 5, 'same.png')]);
    expect(result.kind).toBe('same-icon');
  });
  it('1등과 2등이 너무 가까우면 고르지 않는다', () => {
    // cos(5°) − cos(8°) ≈ 0.006 < MIN_LEAD
    const result = pickBest(card, [entry('A', 5), entry('B', 8)]);
    expect(result.kind).toBe('close');
  });
  it('후보가 없으면 empty', () => {
    const result = pickBest(card, []);
    expect(result.kind).toBe('empty');
  });
  it('기록용으로 상위 3개를 점수 순으로 돌려준다', () => {
    const result = pickBest(card, [entry('C', 60), entry('A', 5), entry('D', 90), entry('B', 30)]);
    expect(result.top.map((t) => t.candidate.name)).toEqual(['A', 'B', 'C']);
  });
});
