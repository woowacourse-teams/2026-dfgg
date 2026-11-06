import * as Sentry from '@sentry/electron/renderer';
import { useEffect, useState } from 'react';

import type { MayhemAugment, MayhemSession } from '../../shared/types';
import { cropIcon, cropTitle, grabFrame, openGameStream } from './lib/capture';
import { applyRead, type CardSlot, EMPTY_SLOT, observeSlot } from './lib/cardSlot';
import { EMPTY_TRACKER, trackCards } from './lib/cardTracker';
import { type Box, fingerprint, ICON_REGION, region, TITLE_REGION } from './lib/detect';

/** 선택 창이 없을 때 확인 주기. 떠 있을 때는 리롤을 바로 따라가려고 더 자주 본다 */
const IDLE_MS = 600;
const OPEN_MS = 250;

export type AugmentCard = {
  /** 게임 화면 비율(0~1) */
  box: Box;
  /** undefined 는 읽는 중, null 은 읽었지만 모르는 증강 */
  augment: MayhemAugment | null | undefined;
};

const relative = (box: Box, width: number, height: number): Box => ({
  x: box.x / width,
  y: box.y / height,
  width: box.width / width,
  height: box.height / height,
});

/**
 * 게임 화면에서 증강 카드를 계속 찾고, 바뀐 카드만 읽어 어떤 증강인지 붙인다.
 * 레벨업·죽음·리롤·몰아서 고르기를 따로 구분하지 않는다. 화면에 카드가 떠 있는지와 바뀌었는지만 본다.
 */
export function useAugmentCards(session: MayhemSession | null): AugmentCard[] | null {
  const [cards, setCards] = useState<AugmentCard[] | null>(null);

  useEffect(() => {
    if (!session) return;
    let stopped = false;
    let timer: number | undefined;
    let closeStream: (() => void) | undefined;
    const slots: CardSlot[] = [EMPTY_SLOT, EMPTY_SLOT, EMPTY_SLOT];
    let tracker = EMPTY_TRACKER;

    const readSlot = async (video: HTMLVideoElement, index: number, card: Box) => {
      const { version } = slots[index];
      try {
        const [title, icon] = await Promise.all([
          cropTitle(video, region(card, TITLE_REGION)),
          cropIcon(video, region(card, ICON_REGION)),
        ]);
        const augment = await window.mayhem.readCard(title, icon);
        if (stopped || slots[index].version !== version) return;
        slots[index] = applyRead(slots[index], version, augment);
        window.analytics.track('mayhem-card', {
          found: augment !== null,
          height: video.videoHeight,
        });
      } catch (error) {
        slots[index] = applyRead(slots[index], version, null);
        Sentry.captureException(error);
      }
    };

    const tick = (video: HTMLVideoElement) => {
      if (stopped) return;
      const frame = grabFrame(video);
      if (frame) tracker = trackCards(tracker, frame);
      const { boxes, candidate } = tracker;

      if (!frame || !boxes) {
        // version 을 올려 창이 닫히기 전에 시작한 OCR 결과가 다음 카드에 붙지 않게 한다
        slots.forEach((slot, index) => {
          slots[index] = { ...EMPTY_SLOT, version: slot.version + 1 };
        });
        setCards(null);
        timer = window.setTimeout(() => tick(video), candidate ? OPEN_MS : IDLE_MS);
        return;
      }

      boxes.forEach((box, index) => {
        const { slot, shouldRead } = observeSlot(slots[index], fingerprint(frame, box));
        slots[index] = slot;
        if (shouldRead) void readSlot(video, index, relative(box, frame.width, frame.height));
      });

      setCards(
        boxes.map((box, index) => ({
          box: relative(box, frame.width, frame.height),
          augment: slots[index].augment,
        })),
      );
      timer = window.setTimeout(() => tick(video), OPEN_MS);
    };

    openGameStream()
      .then(({ video, stop }) => {
        closeStream = stop;
        if (stopped) return stop();
        tick(video);
      })
      .catch((error) => Sentry.captureException(error));

    return () => {
      stopped = true;
      window.clearTimeout(timer);
      closeStream?.();
      setCards(null);
    };
  }, [session]);

  return cards;
}
