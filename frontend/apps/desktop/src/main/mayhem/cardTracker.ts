import { type Box, detectCards, isCard } from './cardDetect';
import type { Gray } from './titleMatch';

// 카드 없음이 이만큼 이어져야 선택 창이 닫힌 것으로 본다. 스킬 효과 같은 한 번 가림은 넘긴다.
const MISSING_TICKS_TO_RELEASE = 2;
// 두 번 찾은 자리가 이 안에서 같으면 멈춘 것으로 본다
const SAME_BOX_TOLERANCE = 0.02;

export type CardTracker = {
  boxes: Box[] | null; // 고정한 박스. 카드가 멈추기 전이면 null
  candidate: Box[] | null; // 직전에 찾은 자리. 다음에도 같으면 고정
  missing: number; // 고정한 뒤 카드가 안 보인 횟수
};

export const EMPTY_TRACKER: CardTracker = { boxes: null, candidate: null, missing: 0 };

function sameBoxes(a: Box[], b: Box[]) {
  return a.every((box, index) => {
    const tolerance = box.width * SAME_BOX_TOLERANCE;
    return (
      Math.abs(box.x - b[index].x) <= tolerance &&
      Math.abs(box.y - b[index].y) <= tolerance &&
      Math.abs(box.width - b[index].width) <= tolerance
    );
  });
}

// 프레임 한 장을 보고 다음 상태를 준다. 리롤 중인 카드는 잠깐 안 보이므로 세 장이 다 안 보인다.
export function trackCards(tracker: CardTracker, frame: Gray): CardTracker {
  if (tracker.boxes) {
    if (tracker.boxes.some((box) => isCard(frame, box))) return { ...tracker, missing: 0 };
    const missing = tracker.missing + 1;
    return missing >= MISSING_TICKS_TO_RELEASE ? EMPTY_TRACKER : { ...tracker, missing };
  }

  const found = detectCards(frame);
  if (found && tracker.candidate && sameBoxes(found, tracker.candidate)) {
    return { boxes: found, candidate: null, missing: 0 };
  }

  return { ...EMPTY_TRACKER, candidate: found };
}
