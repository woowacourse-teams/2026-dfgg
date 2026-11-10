import type { MayhemAugment } from '../../shared/types';
import { printDiff } from './cardDetect';

// 한 카드를 판정하는 최대 횟수. 리롤 직후 프레임은 흐려서 못 읽을 수 있어 두 번까지 다시 읽는다.
const MAX_READS = 3;
// 직전 확인과 지문 차이가 이보다 작으면 카드가 멈춘 것으로 본다. 반짝이는 효과 정도는 넘긴다.
const SETTLE_DIFF = 14;
// 마지막으로 읽은 지문과 차이가 이보다 크면 다시 읽는다. 비슷하게 생긴 카드로 리롤돼도 놓치지 않게 한다.
const CHANGE_DIFF = 5;

export type CardSlot = {
  previous?: number[]; // 직전 확인 때 지문. 카드가 바뀌는 동안은 계속 다랄져서, 멈춘 뒤에만 읽는다.
  read?: number[]; // 마지막으로 읽은 지문. 이것만 달라지면(리롤, 다음 선택) 다시 읽는다.
  augment: MayhemAugment | null | undefined; // undefined는 아직 안 읽음. null은 읽었지만 판정 못함
  attempts: number; // 이 카드를 읽은 횟수
};

export const EMPTY_SLOT: CardSlot = { augment: undefined, attempts: 0 };

// 지문을 보고 다음 상태와 지금 읽을지를 준다. 카드가 바뀐 것 같아도 라벨은 두고 다시 읽어 확인한다. 카드가 안 보이면 바로 지운다.
export function observeSlot(
  slot: CardSlot,
  print: number[],
  visible: boolean,
): { slot: CardSlot; shouldRead: boolean } {
  if (!visible) return { slot: { ...EMPTY_SLOT, previous: print }, shouldRead: false };

  const settled = slot.previous !== undefined && printDiff(slot.previous, print) < SETTLE_DIFF; // 이번 지문이 직접 지문과 같다. 카드가 멈췄다.
  const changed = slot.read !== undefined && printDiff(slot.read, print) > CHANGE_DIFF; // 마지막으로 읽은 지문과 이번 지문이 다르다. 다음 증강 선택으로 넘어갔다.
  const next: CardSlot = changed // 라벨(augment)은 두고 읽은 기록만 지워 다시 읽게 한다. 마우스를 올려도 라벨이 깜빡이지 않는다.
    ? { ...slot, previous: print, read: undefined, attempts: 0 }
    : { ...slot, previous: print };

  // 멈췄는데 아직 안 읽었으면 읽는다.
  if (settled && next.read === undefined) {
    return { slot: { ...next, read: print, attempts: 1 }, shouldRead: true };
  }
  // 읽었는데 판정을 못 했으면(null) 세 번째 시도까지 다시 읽는다. 리롤 직후 흐린 프레임에서 한 번 실패해도 라벨이 끝내 안 뜨는 일이 없게 한다.
  if (settled && next.augment === null && next.attempts < MAX_READS) {
    return { slot: { ...next, read: print, attempts: next.attempts + 1 }, shouldRead: true };
  }
  return { slot: next, shouldRead: false };
}

// 판정 결과를 넣는다
export function applyRead(slot: CardSlot, augment: MayhemAugment | null): CardSlot {
  return { ...slot, augment };
}
