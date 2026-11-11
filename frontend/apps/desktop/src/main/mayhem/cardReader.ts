import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

import type { MayhemAugment, MayhemCard } from '../../shared/types';
import { findWindowRect } from '../docking/clientWindow';
import { type Rect } from '../docking/docking';
import { GAME_WINDOW_TITLE } from '../highlight/highlight';
import { type Box, fingerprint, isCardVisible, region, TITLE_REGION } from './cardDetect';
import { applyRead, applyUnreadable, type CardSlot, EMPTY_SLOT, observeSlot } from './cardSlot';
import { EMPTY_TRACKER, trackCards } from './cardTracker';
import { captureRegionPixels, pixelsToImage, releaseCaptureSurfaces } from './screenCapture';
import { titleMask, toLuma } from './titleStrip';
import type { Gray, Mask } from './titleMatch';
import { reportError } from '../sentry';
import {
  finishMayhemStats,
  recordCheck,
  recordKind,
  recordReadTime,
  recordSelection,
} from './mayhemStats';

// 확인 시간을 모아서 한 줄로 찍는다.
const STATS_EVERY = 20;

// find: 게임 창 찾기, grab: 화면 찍기, luma: 흑백 바꾸기 (판 화면 기준)
type TickStats = {
  count: number;
  total: number;
  find: number;
  grab: number;
  luma: number;
  max: number;
};
const emptyStats = (): TickStats => ({ count: 0, total: 0, find: 0, grab: 0, luma: 0, max: 0 });
type Timing = { grab: number; luma: number };

const OPEN_MS = 500; // 카드가 떠 있거나 뜨는 중일 때 확인 주기 500ms
const IDLE_MS = 500; // 카드가 없을 때 확인 주기 500ms. 카드가 뜬 뒤 첫 확인까지 최대 0.5초
const FRAME_HEIGHT = 720; // 크기 720px
const MAX_DEBUG_FILES = 300;

// 제목 마스크 하나로 어떤 증강인지 판정
export type Judge = (mask: Mask) => MayhemAugment | null;

// 캡처 화면 확인용
const debugDir = process.env.DFGG_CAPTURE_TEST
  ? path.join(app.getPath('userData'), 'capture-test')
  : null;

// 판이 시작될 때 이전 판의 디버그 그림을 지운다. 실패해도 인식은 계속한다.
function resetDebugDir() {
  if (!debugDir) return;
  try {
    fs.mkdirSync(debugDir, { recursive: true });
    for (const name of fs.readdirSync(debugDir)) {
      if (name.endsWith('.png')) fs.rmSync(path.join(debugDir, name), { force: true });
    }
  } catch (error) {
    console.warn('[mayhem] 디버그 폴더 정리 실패', error);
  }
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

/**
 * 화면을 찍어 바로 흑백으로 바꾼다. 찍은 픽셀에서 곧장 흑백을 뽑아
 * 그림(nativeImage)으로 감쌌다 다시 꺼내는 복사를 하지 않는다. 그림은 디버그 저장 때만 만든다.
 */
function captureGray(
  rect: Rect,
  width: number,
  height: number,
  debugName?: string,
  timing?: Timing,
): Gray | null {
  const grabStarted = performance.now();
  const pixels = captureRegionPixels(rect, width, height);
  const grabbed = performance.now();
  if (!pixels) return null;

  if (debugDir && debugName) {
    try {
      fs.writeFileSync(
        path.join(debugDir, `${debugName}.png`),
        pixelsToImage(pixels, width, height).toPNG(),
      );
      pruneDebugDir();
    } catch (error) {
      console.warn('[mayhem] 디버그 그림 저장 실패', error);
    }
  }
  const gray = toLuma(pixels, width, height);
  if (timing) {
    timing.grab = grabbed - grabStarted;
    timing.luma = performance.now() - grabbed;
  }
  return gray;
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
  let stats = emptyStats();
  // 이번 카드 묶음을 처음 본 시각과, 라벨을 띄운 데 걸린 시간. 묶음이 사라지면 한 번 기록한다
  let selectionStarted: number | null = null;
  let labelMs: number | null = null;

  const endSelection = () => {
    if (selectionStarted === null) return;
    recordSelection(labelMs);
    selectionStarted = null;
    labelMs = null;
  };

  /** 확인 한 번의 시간을 더하고, STATS_EVERY 번이 차면 찍고 비운다 */
  const record = (total: number, find: number, timing: Timing) => {
    recordCheck(total, timing.grab, timing.luma);
    stats = {
      count: stats.count + 1,
      total: stats.total + total,
      find: stats.find + find,
      grab: stats.grab + timing.grab,
      luma: stats.luma + timing.luma,
      max: Math.max(stats.max, total),
    };
    if (stats.count < STATS_EVERY) return;
    console.log(
      '[mayhem] 확인',
      `${stats.count}회 평균 ${(stats.total / stats.count).toFixed(0)}ms`,
      `(창 찾기 ${(stats.find / stats.count).toFixed(0)}ms`,
      `찍기 ${(stats.grab / stats.count).toFixed(0)}ms`,
      `흑백 ${(stats.luma / stats.count).toFixed(0)}ms)`,
      `최대 ${stats.max.toFixed(0)}ms`,
    );
    stats = emptyStats();
  };

  const check = (): number => {
    const started = performance.now();

    const game = findWindowRect(GAME_WINDOW_TITLE);
    const found = performance.now() - started;
    const timing: Timing = { grab: 0, luma: 0 };
    const frameWidth = game ? Math.round((game.width / game.height) * FRAME_HEIGHT) : 0;
    const frame = game && captureGray(game, frameWidth, FRAME_HEIGHT, undefined, timing);
    // 창은 있는데 못 찍었다면 캡처 자체가 실패한 것이다. 같은 에러는 한 번만 보낸다
    if (game && !frame) reportError('mayhem-capture', new Error('게임 화면 캡처 실패'));
    if (frame) tracker = trackCards(tracker, frame);

    if (frame && (tracker.boxes || tracker.candidate) && selectionStarted === null) {
      selectionStarted = performance.now(); // 카드 묶음을 처음 봤다
    }

    if (!game || !frame || !tracker.boxes) {
      if (!tracker.candidate) endSelection(); // 카드가 사라졌다
      slots = emptySlots();
      onCards([]);
      record(performance.now() - started, found, timing);
      return tracker.candidate ? OPEN_MS : IDLE_MS;
    }

    const scale = game.height / FRAME_HEIGHT;
    const boxes = tracker.boxes;
    slots = boxes.map((box, i) => {
      const { slot, shouldRead } = observeSlot(
        slots[i],
        fingerprint(frame, box),
        isCardVisible(frame, box),
      );
      if (!shouldRead) return slot;
      const readStarted = performance.now();
      const mask = readTitle(game, box, scale, i);
      if (!mask) recordKind('unreadable');
      const next = mask ? applyRead(slot, judge(mask)) : applyUnreadable(slot);
      recordReadTime(performance.now() - readStarted);
      console.log(
        '[mayhem] 읽기',
        `카드${i + 1}`,
        `${(performance.now() - readStarted).toFixed(0)}ms`,
      );
      return next;
    });

    onCards(
      boxes.map((box, i) => ({ box: relative(box, frame), augment: slots[i].augment ?? null })),
    );
    if (labelMs === null && selectionStarted !== null && slots.some((slot) => slot.augment)) {
      labelMs = performance.now() - selectionStarted; // 이번 묶음의 첫 라벨
    }
    record(performance.now() - started, found, timing);
    return OPEN_MS;
  };

  const tick = () => {
    if (!alive()) {
      releaseCaptureSurfaces(); // 판이 끝나면 찍기용 자원을 돌려준다
      endSelection();
      finishMayhemStats(); // 한 판 동안 모은 인식 통계를 보낸다
      return;
    }
    let delay = OPEN_MS;
    try {
      delay = check();
    } catch (error) {
      console.error('[mayhem] 확인 실패', error);
      reportError('mayhem-card-reader', error);
    }
    setTimeout(tick, delay);
  };
  tick();
}
