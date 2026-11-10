import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

import type { MayhemAugment, MayhemCard } from '../../shared/types';
import { findWindowRect } from '../docking/clientWindow';
import { type Rect } from '../docking/docking';
import { GAME_WINDOW_TITLE } from '../highlight/highlight';
import { type Box, fingerprint, isCard, region, TITLE_REGION } from './cardDetect';
import { applyRead, type CardSlot, EMPTY_SLOT, observeSlot } from './cardSlot';
import { EMPTY_TRACKER, trackCards } from './cardTracker';
import { captureRegion } from './screenCapture';
import { titleMask, toLuma } from './titleStrip';
import type { Gray, Mask } from './titleMatch';

const OPEN_MS = 500; // 카드가 떠 있거나 뜨는 중일 때 확인 주기 500ms
const IDLE_MS = 1000; // 카드가 없을 때 확인 주기 1초
const FRAME_HEIGHT = 720; // 크기 720px
const MAX_DEBUG_FILES = 300;

// 제목 마스크 하나로 어떤 증강인지 판정
export type Judge = (mask: Mask) => MayhemAugment | null;

// 캡처 화면 확인용
const debugDir = process.env.DFGG_CAPTURE_TEST
  ? path.join(app.getPath('userData'), 'capture-test')
  : null;

// 판이 시작될 때 이전 판의 디버그 사진을 지운다.
function resetDebugDir() {
  if (!debugDir) return;
  fs.rmSync(debugDir, { recursive: true, force: true });
  fs.mkdirSync(debugDir, { recursive: true });
}

// 그림이 MAX_DEBUG_FILES를 넘으면 오래된 것부터 지운다.
function pruneDebugDir() {
  if (!debugDir) return;
  const files = fs
    .readdirSync(debugDir)
    .filter((name) => name.endsWith('.png'))
    .sort();
  for (const name of files.slice(0, Math.max(0, files.length - MAX_DEBUG_FILES))) {
    fs.rmSync(path.join(debugDir, name), { force: true });
  }
}

const emptySlots = (): CardSlot[] => [EMPTY_SLOT, EMPTY_SLOT, EMPTY_SLOT];

function captureGray(rect: Rect, width: number, height: number, debugName?: string): Gray | null {
  const image = captureRegion(rect, width, height);
  if (!image) return null;
  if (debugDir && debugName) {
    fs.writeFileSync(path.join(debugDir, `${debugName}.png`), image.toPNG());
    pruneDebugDir();
  }
  return toLuma(image.toBitmap(), width, height);
}

// 카드 제목 영역을 원래 해상도로 찍어 마스크로 만든다.
function readTitle(game: Rect, card: Box, scale: number, index: number): Mask | null {
  const title = region(card, TITLE_REGION);
  const rect = {
    x: Math.round(game.x + title.x * scale),
    y: Math.round(game.y + title.y * scale),
    width: Math.round(title.width * scale),
    height: Math.round(title.height * scale),
  };
  const strip = captureGray(rect, rect.width, rect.height, `${Date.now()}-${index}`);
  return strip && titleMask(strip, card.height * scale);
}

const relative = (box: Box, frame: Gray): Box => ({
  x: box.x / frame.width,
  y: box.y / frame.height,
  width: box.width / frame.width,
  height: box.height / frame.height,
});

// 판이 진행되는 동안 화면에서 증강 카드를 찾고, 바뀐 카드만 읽어 판정한다.
// 매번 카드 세 장의 위치와 판정 결과를 onCards로 넘긴다.
export function startCardReader(
  alive: () => boolean,
  judge: Judge,
  onCards: (cards: MayhemCard[]) => void,
) {
  resetDebugDir();

  let tracker = EMPTY_TRACKER;
  let slots = emptySlots();

  const tick = () => {
    if (!alive()) return;

    const game = findWindowRect(GAME_WINDOW_TITLE);
    const frameWidth = game ? Math.round((game.width / game.height) * FRAME_HEIGHT) : 0;
    const frame = game && captureGray(game, frameWidth, FRAME_HEIGHT);
    if (frame) tracker = trackCards(tracker, frame);

    if (!game || !frame || !tracker.boxes) {
      slots = emptySlots();
      onCards([]);
      setTimeout(tick, tracker.candidate ? OPEN_MS : IDLE_MS);
      return;
    }

    const scale = game.height / FRAME_HEIGHT;
    const boxes = tracker.boxes;
    slots = boxes.map((box, i) => {
      const { slot, shouldRead } = observeSlot(
        slots[i],
        fingerprint(frame, box),
        isCard(frame, box),
      );
      if (!shouldRead) return slot;
      const mask = readTitle(game, box, scale, i);
      return applyRead(slot, mask && judge(mask));
    });

    onCards(
      boxes.map((box, i) => ({ box: relative(box, frame), augment: slots[i].augment ?? null })),
    );
    setTimeout(tick, OPEN_MS);
  };
  tick();
}
