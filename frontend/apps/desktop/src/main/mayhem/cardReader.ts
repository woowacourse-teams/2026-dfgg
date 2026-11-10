import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

import { findWindowRect } from '../docking/clientWindow';
import { type Rect } from '../docking/docking';
import { GAME_WINDOW_TITLE } from '../highlight/highlight';
import { detectCards, region, TITLE_REGION } from './cardDetect';
import { captureRegion } from './screenCapture';
import { titleMask, toLuma } from './titleStrip';
import type { Mask } from './titleMatch';

const READ_MS = 1000;
const FRAME_HEIGHT = 720;

// 카드 세 장의 제목 마스크
export type CardTitles = (Mask | null)[];

const debugDir = process.env.DFGG_CAPTURE_TEST
  ? path.join(app.getPath('userData'), 'capture-test')
  : null;

// 게임 창 전체를 작게 찍어 흑백으로 바꾼다. 이 그림은 카드를 찾는 데만 쓴다.
function captureGray(rect: Rect, width: number, height: number, debugName?: string) {
  const image = captureRegion(rect, width, height);
  if (!image) return null;
  if (debugDir && debugName) {
    fs.mkdirSync(debugDir, { recursive: true });
    fs.writeFileSync(path.join(debugDir, `${debugName}.png`), image.toPNG());
  }
  return toLuma(image.toBitmap(), width, height);
}

// 게임 화면 위에 증강 제목을 읽는다.
export function readCardTitles(): CardTitles | null {
  const game = findWindowRect(GAME_WINDOW_TITLE);
  if (!game) return null; // 게임 창이 안 보임 (알트탭 등)

  const frameWidth = Math.round((game.width / game.height) * FRAME_HEIGHT);
  const frame = captureGray(game, frameWidth, FRAME_HEIGHT);
  if (!frame) return null;

  const cards = detectCards(frame);
  if (!cards) return null; // 증강 선택 화면이 아님

  const scale = game.height / FRAME_HEIGHT;
  const stamp = Date.now();
  return cards.map((card, i) => {
    const title = region(card, TITLE_REGION);
    const rect = {
      x: Math.round(game.x + title.x * scale),
      y: Math.round(game.y + title.y * scale),
      width: Math.round(title.width * scale),
      height: Math.round(title.height * scale),
    };
    const strip = captureGray(rect, rect.width, rect.height, `${stamp}-${i}`);
    return strip && titleMask(strip, card.height * scale);
  });
}

// 게임 판이 진행되는 동안 1초마다 화면을 읽어서, 증강 카드가 보이면 세 장의 제목 마스크를 콜백으로 넘겨줌
export function startCardReader(alive: () => boolean, onTitles: (titles: CardTitles) => void) {
  const timer = setInterval(() => {
    if (!alive()) {
      clearInterval(timer);
      return;
    }
    const started = performance.now();
    const titles = readCardTitles();
    if (titles) {
      console.log('[mayhem] 읽기', `${(performance.now() - started).toFixed(0)}ms`);
      onTitles(titles);
    }
  }, READ_MS);
}
