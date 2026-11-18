import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';

import { findWindowRect } from '../docking/clientWindow';
import { GAME_WINDOW_TITLE } from '../highlight/highlight';
import { captureRegion } from './screenCapture';

const INTERVAL_MS = 3000;
const CAPTURE_HEIGHT = 720;

/** 실험용. DFGG_CAPTURE_TEST=1 일 때만 판 중에 3초마다 게임 화면을 찍어 저장한다 */
export function startCaptureTest(alive: () => boolean) {
  if (!process.env.DFGG_CAPTURE_TEST) return;

  const dir = path.join(app.getPath('userData'), 'capture-test');
  fs.mkdirSync(dir, { recursive: true });
  console.log('[capture-test] 저장 위치', dir);

  const timer = setInterval(() => {
    if (!alive()) {
      clearInterval(timer);
      return;
    }
    const rect = findWindowRect(GAME_WINDOW_TITLE);
    if (!rect) return; // 게임 창이 안 보이면(알트탭) 건너뜀

    const width = Math.round((rect.width / rect.height) * CAPTURE_HEIGHT);
    const started = performance.now();
    const image = captureRegion(rect, width, CAPTURE_HEIGHT);
    const ms = performance.now() - started;

    console.log(
      '[capture-test]',
      `${rect.width}x${rect.height} → ${width}x${CAPTURE_HEIGHT}`,
      `${ms.toFixed(1)}ms`,
      image ? 'ok' : 'fail',
    );
    if (image) fs.writeFileSync(path.join(dir, `${Date.now()}.png`), image.toPNG());
  }, INTERVAL_MS);
}
