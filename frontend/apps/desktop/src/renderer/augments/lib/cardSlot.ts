import type { MayhemAugment } from '../../../shared/types';
import { isSameCard } from './detect';

/** 한 카드를 읽는 최대 횟수. 리롤 직후 프레임은 흐려서 못 읽을 수 있어 두 번까지 다시 읽는다 */
const MAX_READS = 3;

export type CardSlot = {
  /** 직전 확인 때 지문. 카드가 바뀌는 동안은 계속 달라져서, 멈춘 뒤에만 읽는다 */
  previous?: number[];
  /** 마지막으로 읽은 지문. 이것과 달라지면(리롤·다음 선택) 다시 읽는다 */
  read?: number[];
  /** undefined 는 읽는 중이거나 아직 안 읽음, null 은 읽었지만 모르는 증강 */
  augment: MayhemAugment | null | undefined;
  /** 이 카드를 읽은 횟수 */
  attempts: number;
  /** 늦게 끝난 옛 OCR 결과가 새 카드를 덮지 않게 */
  version: number;
};

export const EMPTY_SLOT: CardSlot = { augment: undefined, attempts: 0, version: 0 };

function startRead(slot: CardSlot, print: number[], attempts: number) {
  return {
    slot: { ...slot, read: print, augment: undefined, attempts, version: slot.version + 1 },
    shouldRead: true,
  };
}

/** 틱마다 지문을 보고 다음 상태와 읽을지 여부를 준다. 카드가 바뀌면 틀린 딱지가 남지 않게 그 틱에 바로 지운다 */
export function observeSlot(slot: CardSlot, print: number[]) {
  const settled = isSameCard(slot.previous, print);
  const changed = slot.read !== undefined && !isSameCard(slot.read, print);
  const next: CardSlot = changed
    ? {
        ...slot,
        previous: print,
        read: undefined,
        augment: undefined,
        attempts: 0,
        version: slot.version + 1,
      }
    : { ...slot, previous: print };

  if (settled && next.read === undefined) return startRead(next, print, 1);
  if (settled && next.augment === null && next.attempts < MAX_READS) {
    return startRead(next, print, next.attempts + 1);
  }
  return { slot: next, shouldRead: false };
}

/** 읽기 결과를 넣는다. 그사이 카드가 바뀌었으면 버린다 */
export function applyRead(
  slot: CardSlot,
  version: number,
  augment: MayhemAugment | null,
): CardSlot {
  return slot.version === version ? { ...slot, augment } : slot;
}
