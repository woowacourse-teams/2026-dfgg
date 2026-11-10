import { describe, expect, it } from 'vitest';
import type { MayhemAugment } from '../../../shared/types';
import { applyRead, EMPTY_SLOT, observeSlot, type CardSlot } from '../cardSlot';

const A = Array(144).fill(10);
const B = Array(144).fill(200);
const AUGMENT = { augmentId: 1, name: '핀볼' } as MayhemAugment;

/** 같은 지문을 두 번 보여 줘서 읽을 차례까지 간다 */
function settle(print: number[]) {
  const first = observeSlot(EMPTY_SLOT, print);
  return observeSlot(first.slot, print);
}

describe('observeSlot', () => {
  it('처음 본 카드는 바로 읽지 않는다', () => {
    expect(observeSlot(EMPTY_SLOT, A).shouldRead).toBe(false);
  });

  it('지문이 두 번 같으면 읽는다', () => {
    expect(settle(A).shouldRead).toBe(true);
  });

  it('읽은 뒤 같은 카드면 다시 읽지 않는다', () => {
    const next = observeSlot(applyRead(settle(A).slot, AUGMENT), A);
    expect(next.shouldRead).toBe(false);
    expect(next.slot.augment).toBe(AUGMENT);
  });

  it('카드가 바뀌면 그 순간 라벨을 지우고, 멈추면 다시 읽는다', () => {
    const next = observeSlot(applyRead(settle(A).slot, AUGMENT), B);
    expect(next.slot.augment).toBeUndefined();
    expect(next.shouldRead).toBe(false);
    expect(observeSlot(next.slot, B).shouldRead).toBe(true);
  });

  it('판정 못 하면 최대 세 번까지 읽는다', () => {
    let slot: CardSlot = applyRead(settle(A).slot, null);
    let reads = 1;
    for (let i = 0; i < 5; i++) {
      const next = observeSlot(slot, A);
      if (next.shouldRead) reads++;
      slot = next.shouldRead ? applyRead(next.slot, null) : next.slot;
    }
    expect(reads).toBe(3);
  });
});
