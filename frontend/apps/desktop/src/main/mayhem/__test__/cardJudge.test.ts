import { describe, expect, it, vi } from 'vitest';
import type { MayhemAugment } from '../../../shared/types';
import { judgeCard, sameMask, type Slot } from '../cardJudge';
import type { Mask } from '../titleMatch';

vi.spyOn(console, 'log').mockImplementation(() => {});

function mask(width: number, fill: number): Mask {
  return { width, height: 32, data: new Float32Array(width * 32).fill(fill) };
}

const AUGMENT = { augmentId: 1, name: '핀볼' } as MayhemAugment;

describe('sameMask', () => {
  it('같은 마스크면 true', () => {
    expect(sameMask(mask(50, 0.5), mask(50, 0.52))).toBe(true);
  });

  it('픽셀 차이가 크면 false', () => {
    expect(sameMask(mask(50, 0), mask(50, 1))).toBe(false);
  });

  it('폭이 다르면 false', () => {
    expect(sameMask(mask(50, 0.5), mask(51, 0.5))).toBe(false);
  });
});

describe('judgeCard', () => {
  it('제목이 그대로면 이전 결과를 쓴다', () => {
    const previous: Slot = { mask: mask(50, 0.5), augment: AUGMENT };
    expect(judgeCard(mask(50, 0.5), [], previous)).toBe(previous);
  });

  it('제목이 바뀌면 다시 판정한다', () => {
    const previous: Slot = { mask: mask(50, 0.5), augment: AUGMENT };
    const slot = judgeCard(mask(60, 0.5), [], previous);
    expect(slot.augment).toBeNull(); // 후보가 없으니 판정 불가
    expect(slot).not.toBe(previous);
  });
});
