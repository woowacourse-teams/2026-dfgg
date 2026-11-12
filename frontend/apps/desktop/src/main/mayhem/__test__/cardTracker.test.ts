import { describe, expect, it } from 'vitest';

import {
  CARD_BOXES,
  CARD_SCREENS,
  loadFrame,
  NO_CARD_SCREEN,
} from '../../../../test/fixtures/augments/frame';
import type { Box } from '../cardDetect';
import { type CardTracker, EMPTY_TRACKER, trackCards } from '../cardTracker';
import type { Gray } from '../titleMatch';

const cardScreen = loadFrame(CARD_SCREENS[2]);
const otherCardScreen = loadFrame(CARD_SCREENS[3]);
const noCards = loadFrame(NO_CARD_SCREEN);

const locked = (): CardTracker => trackCards(trackCards(EMPTY_TRACKER, cardScreen), cardScreen);

/** 화면을 오른쪽으로 민다. 카드가 날아 들어오는 동안처럼 자리가 바뀐 프레임 */
function shifted(frame: Gray, pixels: number): Gray {
  const data = new Uint8Array(frame.data.length);
  data.set(frame.data.subarray(0, frame.data.length - pixels), pixels);
  return { ...frame, data };
}

/** 카드 한 장을 하얗게 덮는다. 리롤로 그 카드만 번쩍이는 순간 */
function coverCard(frame: Gray, card: Box): Gray {
  const data = frame.data.slice();
  for (let y = Math.round(card.y); y < card.y + card.height; y++) {
    data.fill(
      255,
      y * frame.width + Math.round(card.x),
      y * frame.width + Math.round(card.x + card.width),
    );
  }
  return { ...frame, data };
}

describe('카드 박스 고정', () => {
  it('처음 찾은 자리는 아직 고정하지 않는다', () => {
    expect(trackCards(EMPTY_TRACKER, cardScreen).boxes).toBeNull();
  });

  it('같은 자리가 두 번 이어지면 고정한다', () => {
    expect(locked().boxes).toHaveLength(3);
  });

  it('자리가 바뀌는 동안에는 고정하지 않는다', () => {
    const moving = trackCards(EMPTY_TRACKER, shifted(cardScreen, 12));
    expect(trackCards(moving, cardScreen).boxes).toBeNull();
  });

  it('고정한 뒤에는 화면이 바뀌어도 박스가 그대로다', () => {
    const before = locked();
    expect(trackCards(before, otherCardScreen).boxes).toBe(before.boxes);
  });

  it('카드가 없는 화면만 이어지면 고정하지 않는다', () => {
    expect(trackCards(trackCards(EMPTY_TRACKER, noCards), noCards).boxes).toBeNull();
  });
});

describe('카드 박스 해제', () => {
  it('한 번 사라져도 박스를 지킨다', () => {
    const before = locked();
    const missedOnce = trackCards(before, noCards);
    const missedAgainLater = trackCards(trackCards(missedOnce, cardScreen), noCards);
    expect(missedOnce.boxes).toBe(before.boxes);
    expect(missedAgainLater.boxes).toBe(before.boxes);
  });

  it('연속 두 번 사라지면 푼다', () => {
    expect(trackCards(trackCards(locked(), noCards), noCards)).toEqual(EMPTY_TRACKER);
  });

  it('한 장만 가려져도 풀지 않는다', () => {
    const before = locked();
    const rerolling = coverCard(cardScreen, CARD_BOXES[0]);
    expect(trackCards(trackCards(before, rerolling), rerolling).boxes).toBe(before.boxes);
  });
});
