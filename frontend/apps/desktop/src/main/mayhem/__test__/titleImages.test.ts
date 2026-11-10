import { describe, expect, it, vi } from 'vitest';

vi.mock('electron', () => ({ nativeImage: {} }));

import { bitmapToGray } from '../titleImages';

describe('bitmapToGray', () => {
  it('픽셀마다 첫 바이트만 꺼낸다', () => {
    const bitmap = new Uint8Array([
      10, 10, 10, 255, 200, 200, 200, 255, 0, 0, 0, 255, 255, 255, 255, 255,
    ]);
    const gray = bitmapToGray(bitmap, 2, 2);
    expect(gray.width).toBe(2);
    expect(gray.height).toBe(2);
    expect([...gray.data]).toEqual([10, 200, 0, 255]);
  });
});
