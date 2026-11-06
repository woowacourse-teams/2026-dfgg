import { type Box, detectCards, type Frame, isCard } from './detect';

/** 카드 없음이 이만큼 이어져야 창이 닫힌 것으로 본다. 스킬 효과 같은 한 틱 가림은 넘긴다 */
const MISSING_TICKS_TO_RELEASE = 2;
/** 두 번 찾은 자리가 이 안에서 같으면 멈춘 것으로 본다 (카드 폭 비율). 강조 빛으로 1픽셀쯤은 움직인다 */
const SAME_BOX_TOLERANCE = 0.02;

/**
 * 카드는 선택 창이 떠 있는 동안 움직이지 않는다. 멈춘 자리를 한 번 잡으면 창이 닫힐 때까지 그대로 쓴다.
 * 틱마다 다시 찾으면 박스가 조금씩 흔들려서 카드가 바뀐 것처럼 보이기 때문이다.
 */
export type CardTracker = {
  /** 고정한 박스. 카드가 멈추기 전이면 null */
  boxes: Box[] | null;
  /** 직전 틱에 찾은 자리. 다음 틱에도 같으면 고정한다 */
  candidate: Box[] | null;
  /** 고정한 뒤 카드가 안 보인 틱 수 */
  missing: number;
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

/** 프레임 한 장을 보고 다음 상태를 준다. 리롤 중인 카드는 잠깐 안 보이므로 세 장이 다 안 보여야 없다고 본다 */
export function trackCards(tracker: CardTracker, frame: Frame): CardTracker {
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
