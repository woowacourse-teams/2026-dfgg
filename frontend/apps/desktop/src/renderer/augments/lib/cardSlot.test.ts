import { describe, expect, it } from 'vitest';

import type { MayhemAugment } from '../../../shared/types';
import { applyRead, type CardSlot, EMPTY_SLOT, observeSlot } from './cardSlot';

const CARD_A = [0, 0, 0];
const CARD_B = [100, 100, 100];
const augment = { augmentId: 1 } as MayhemAugment;

/** 카드 하나를 읽기 시작한 슬롯 */
function reading(print: number[]): CardSlot {
  const first = observeSlot(EMPTY_SLOT, print).slot;
  return observeSlot(first, print).slot;
}

/** 읽기가 끝난 슬롯 */
function finished(print: number[], result: MayhemAugment | null): CardSlot {
  const slot = reading(print);
  return applyRead(slot, slot.version, result);
}

describe('카드 읽기 시점', () => {
  it('처음 본 카드는 멈출 때까지 읽지 않는다', () => {
    expect(observeSlot(EMPTY_SLOT, CARD_A).shouldRead).toBe(false);
  });

  it('지문이 두 번 이어지면 읽는다', () => {
    const first = observeSlot(EMPTY_SLOT, CARD_A).slot;

    expect(observeSlot(first, CARD_A).shouldRead).toBe(true);
  });

  it('읽는 동안에는 다시 읽지 않는다', () => {
    expect(observeSlot(reading(CARD_A), CARD_A).shouldRead).toBe(false);
  });

  it('읽은 카드가 그대로면 다시 읽지 않고 결과를 유지한다', () => {
    const { slot, shouldRead } = observeSlot(finished(CARD_A, augment), CARD_A);

    expect(shouldRead).toBe(false);
    expect(slot.augment).toBe(augment);
  });
});

describe('카드가 바뀔 때', () => {
  it('지문이 바뀐 틱에 옛 증강을 바로 지운다', () => {
    const { slot } = observeSlot(finished(CARD_A, augment), CARD_B);

    expect(slot.augment).toBeUndefined();
  });

  it('바뀌는 중에는 읽지 않고, 멈추면 읽는다', () => {
    const changing = observeSlot(finished(CARD_A, augment), CARD_B);

    expect(changing.shouldRead).toBe(false);
    expect(observeSlot(changing.slot, CARD_B).shouldRead).toBe(true);
  });

  it('바뀌기 전에 시작한 읽기 결과는 버린다', () => {
    const before = reading(CARD_A);
    const { slot } = observeSlot(before, CARD_B);

    expect(applyRead(slot, before.version, augment).augment).toBeUndefined();
  });
});

describe('읽기 실패', () => {
  /** 실패 결과를 주고 다음 틱을 본다 */
  const failThenObserve = (slot: CardSlot) =>
    observeSlot(applyRead(slot, slot.version, null), CARD_A);

  it('실패하면 최대 두 번 더 읽고 멈춘다', () => {
    const first = failThenObserve(reading(CARD_A));
    const second = failThenObserve(first.slot);
    const third = failThenObserve(second.slot);

    expect([first.shouldRead, second.shouldRead, third.shouldRead]).toEqual([true, true, false]);
  });

  it('다시 읽어 성공하면 더 읽지 않는다', () => {
    const retry = failThenObserve(reading(CARD_A)).slot;
    const success = applyRead(retry, retry.version, augment);

    const { slot, shouldRead } = observeSlot(success, CARD_A);

    expect(shouldRead).toBe(false);
    expect(slot.augment).toBe(augment);
  });

  it('카드가 바뀌면 시도 횟수를 처음부터 센다', () => {
    const exhausted = failThenObserve(failThenObserve(failThenObserve(reading(CARD_A)).slot).slot);
    const failed = applyRead(exhausted.slot, exhausted.slot.version, null);

    const changing = observeSlot(failed, CARD_B).slot;
    const reread = observeSlot(changing, CARD_B);
    const retried = observeSlot(applyRead(reread.slot, reread.slot.version, null), CARD_B);

    expect(reread.shouldRead).toBe(true);
    expect(retried.shouldRead).toBe(true);
  });
});
