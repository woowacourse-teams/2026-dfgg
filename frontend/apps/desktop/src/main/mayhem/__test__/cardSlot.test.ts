import { describe, expect, it } from 'vitest';
import type { MayhemAugment } from '../../../shared/types';
import { applyRead, applyUnreadable, type CardSlot, EMPTY_SLOT, observeSlot } from '../cardSlot';

const print = (value: number) => Array(144).fill(value);
const A = print(10);
const HOVER = print(17); // 차이 7: 마우스 올림
const SHIMMER = print(13); // 차이 3: 반짝임
const B = print(40); // 차이 30: 다른 카드
const X = { augmentId: 1, name: '핀볼' } as MayhemAugment;
const Y = { augmentId: 2, name: '속행' } as MayhemAugment;

/** 같은 지문을 두 번 보여 줘서 읽을 차례까지 간다 */
function settle(value: number[]) {
  return observeSlot(observeSlot(EMPTY_SLOT, value, true).slot, value, true);
}

describe('observeSlot', () => {
  it('처음 본 카드는 멈춘 뒤에 읽는다', () => {
    expect(observeSlot(EMPTY_SLOT, A, true).shouldRead).toBe(false);
    expect(settle(A).shouldRead).toBe(true);
  });

  it('마우스를 올려도 라벨을 지키고 다시 읽는다', () => {
    const next = observeSlot(applyRead(settle(A).slot, X), HOVER, true);
    expect(next.slot.augment).toBe(X);
    expect(next.shouldRead).toBe(true);
  });

  it('반짝임만으로는 다시 읽지 않는다', () => {
    expect(observeSlot(applyRead(settle(A).slot, X), SHIMMER, true).shouldRead).toBe(false);
  });

  it('다른 카드로 바뀌면 멈출 때까지 옛 라벨을 두고, 읽으면 바꾼다', () => {
    const moving = observeSlot(applyRead(settle(A).slot, X), B, true);
    expect(moving.slot.augment).toBe(X);
    expect(moving.shouldRead).toBe(false);

    const settled = observeSlot(moving.slot, B, true);
    expect(settled.shouldRead).toBe(true);
    expect(applyRead(settled.slot, Y).augment).toBe(Y);
  });

  it('카드가 가려져 있으면 라벨을 지키고 읽지 않는다', () => {
    const covered = observeSlot(applyRead(settle(A).slot, X), B, false);
    expect(covered.slot.augment).toBe(X);
    expect(covered.shouldRead).toBe(false);
  });

  it('가려졌다 다시 보이면 같은 카드는 다시 읽지 않는다', () => {
    const read = applyRead(settle(A).slot, X);
    const covered = observeSlot(read, B, false);
    expect(observeSlot(covered.slot, A, true).shouldRead).toBe(false);
  });

  it('다시 읽다 제목을 못 읽으면 이전 라벨을 지킨다', () => {
    const rereading = observeSlot(applyRead(settle(A).slot, X), HOVER, true);
    expect(rereading.shouldRead).toBe(true);
    expect(applyUnreadable(rereading.slot).augment).toBe(X);
  });

  it('다른 카드로 바뀌었는데 목록에 없으면(파편 등) 이전 라벨을 지운다', () => {
    const moving = observeSlot(applyRead(settle(A).slot, X), B, true);
    const settled = observeSlot(moving.slot, B, true);
    expect(settled.shouldRead).toBe(true);
    expect(applyRead(settled.slot, null).augment).toBeNull();
  });

  it('판정 못 하면 최대 세 번까지 읽는다', () => {
    let slot: CardSlot = applyRead(settle(A).slot, null);
    let reads = 1;
    for (let i = 0; i < 5; i++) {
      const next = observeSlot(slot, A, true);
      if (next.shouldRead) reads++;
      slot = next.shouldRead ? applyRead(next.slot, null) : next.slot;
    }
    expect(reads).toBe(3);
  });
});
