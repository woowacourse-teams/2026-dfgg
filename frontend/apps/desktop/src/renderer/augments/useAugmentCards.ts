import * as Sentry from '@sentry/electron/renderer';
import { useEffect, useState } from 'react';

import type { MayhemAugment, MayhemSession } from '../../shared/types';
import { cropIcon, cropTitle, grabFrame, openGameStream } from './lib/capture';
import {
  type Box,
  detectCards,
  fingerprint,
  ICON_REGION,
  isSameCard,
  region,
  TITLE_REGION,
} from './lib/detect';

/** 선택 창이 없을 때 확인 주기. 떠 있을 때는 리롤을 바로 따라가려고 더 자주 본다 */
const IDLE_MS = 600;
const OPEN_MS = 250;

export type AugmentCard = {
  /** 게임 화면 비율(0~1) */
  box: Box;
  /** undefined 는 읽는 중, null 은 읽었지만 모르는 증강 */
  augment: MayhemAugment | null | undefined;
};

type Slot = {
  /** 직전 확인 때 지문. 카드가 날아 들어오는 동안은 계속 바뀌어서, 멈춘 뒤에만 읽는다 */
  previous?: number[];
  /** 마지막으로 읽은 지문. 이것과 달라지면(리롤·다음 선택) 다시 읽는다 */
  read?: number[];
  augment: MayhemAugment | null | undefined;
  /** 늦게 끝난 옛 OCR 결과가 새 카드를 덮지 않게 */
  version: number;
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
    const slots: Slot[] = [0, 1, 2].map(() => ({ augment: undefined, version: 0 }));

    const readSlot = async (video: HTMLVideoElement, index: number, card: Box) => {
      const slot = slots[index];
      const version = ++slot.version;
      slot.augment = undefined;
      try {
        const [title, icon] = await Promise.all([
          cropTitle(video, region(card, TITLE_REGION)),
          cropIcon(video, region(card, ICON_REGION)),
        ]);
        const augment = await window.mayhem.readCard(title, icon);
        if (stopped || slot.version !== version) return;
        slot.augment = augment;
        window.analytics.track('mayhem-card', {
          found: augment !== null,
          height: video.videoHeight,
        });
      } catch (error) {
        if (slot.version === version) slot.augment = null;
        Sentry.captureException(error);
      }
    };

    const tick = (video: HTMLVideoElement) => {
      if (stopped) return;
      const frame = grabFrame(video);
      const boxes = frame && detectCards(frame);

      if (!frame || !boxes) {
        slots.forEach((slot) => Object.assign(slot, { previous: undefined, read: undefined }));
        setCards(null);
        timer = window.setTimeout(() => tick(video), IDLE_MS);
        return;
      }

      boxes.forEach((box, index) => {
        const slot = slots[index];
        const print = fingerprint(frame, box);
        const settled = isSameCard(slot.previous, print);
        slot.previous = print;
        if (settled && !isSameCard(slot.read, print)) {
          slot.read = print;
          void readSlot(video, index, relative(box, frame.width, frame.height));
        }
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
