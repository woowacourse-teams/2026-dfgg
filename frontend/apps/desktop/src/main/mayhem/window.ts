import { BrowserWindow, screen } from 'electron';

import { findWindowRect } from '../docking/clientWindow';
import { type Rect } from '../docking/docking';
import { GAME_WINDOW_TITLE } from '../highlight/highlight';

const FOLLOW_MS = 1000;

let createWindow: (() => BrowserWindow) | null = null; // 창 만드는 함수
let augmentWindow: BrowserWindow | null = null; // 지금 떠 있는 증강 창
let followTimer: NodeJS.Timeout | null = null; // 게임 창을 따라가는 타이머

export function setWindowFactory(create: () => BrowserWindow) {
  createWindow = create;
}

// IPC를 보낸 창이 증강 창인지 확인
export function isAugmentWindow(sender: Electron.WebContents) {
  return !!augmentWindow && !augmentWindow.isDestroyed() && sender === augmentWindow.webContents;
}

const sameRect = (a: Rect, b: Rect) =>
  a.x === b.x && a.y === b.y && a.width === b.width && a.height === b.height;

// 증강 창을 게임 화면에 딱 겹친다. 게임 창이 안 보이면 숨긴다
function followGameWindow(win: BrowserWindow) {
  let last: Rect | null = null;

  const follow = () => {
    if (win.isDestroyed()) return;

    const rect = findWindowRect(GAME_WINDOW_TITLE);
    if (!rect) {
      last = null;
      if (win.isVisible()) win.hide();
      return;
    }

    // 윈도우 배율이면 물리 픽셀과 창 좌표가 달라 바꾼다.
    const bounds = screen.screenToDipRect(win, rect);
    if (!last || !sameRect(last, bounds)) win.setBounds(bounds);
    last = bounds;

    if (!win.isVisible()) win.showInactive();
  };

  follow();
  followTimer = setInterval(follow, FOLLOW_MS);
}

export function openWindow() {
  if (!createWindow || augmentWindow) return;
  augmentWindow = createWindow();
  followGameWindow(augmentWindow);
}

export function closeWindow() {
  if (followTimer) clearInterval(followTimer);
  followTimer = null;
  if (augmentWindow && !augmentWindow.isDestroyed()) augmentWindow.destroy();
  augmentWindow = null;
}
