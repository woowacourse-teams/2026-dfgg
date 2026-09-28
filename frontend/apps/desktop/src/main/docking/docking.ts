import { BrowserWindow, screen } from 'electron';
import { trackEvent } from '../analytics/umami';
import { onPhaseChange, onStatusChange } from '../lcu/state';
import { findWindowRect } from './clientWindow';

export const LOL_CLIENT_TITLE = 'League of Legends';

/** 롤 창과 우리 창 사이 간격. 0 이면 딱 붙는다. */
const GAP = -4;

/** 라이브러리 내부가 30fps 로 갱신하므로 같은 주기를 쓴다. */
const THROTTLE_MS = 34;

export type Rect = { x: number; y: number; width: number; height: number };

const FIND_INTERVAL_MS = 500;
const FIND_TIMEOUT_MS = 30_000;

let findTimer: NodeJS.Timeout | null = null;

/**
 * 네이티브 모듈이라 환경(OS·아키텍처·Electron 버전)에 따라 로드가 실패할 수 있다.
 * 도킹은 부가 기능이므로, 실패해도 앱 나머지는 그대로 동작해야 한다.
 * import 문은 최상단에서 무조건 실행돼 막을 수 없어 require 를 쓴다.
 */
function loadOverlayController() {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const mod = require('electron-overlay-window') as typeof import('electron-overlay-window');
    return mod.OverlayController;
  } catch (error) {
    console.warn('[도킹] 네이티브 모듈 로드 실패 — 도킹 없이 계속합니다', error);
    trackEvent('desktop-error', { type: 'docking-module' });
    return null;
  }
}

/**
 * 마지막 호출도 반드시 반영되는 throttle.
 * 드래그가 멈춘 직후의 좌표를 놓치면 창이 어긋난 자리에 남는다.
 */
function throttle(ms: number, fn: (rect: Rect) => void) {
  let lastRun = 0;
  let pending: Rect | null = null;
  let timer: NodeJS.Timeout | null = null;

  return (rect: Rect) => {
    const wait = ms - (Date.now() - lastRun);

    if (wait <= 0) {
      lastRun = Date.now();
      fn(rect);
      return;
    }

    pending = rect;
    if (timer) return;

    timer = setTimeout(() => {
      timer = null;
      lastRun = Date.now();
      if (pending) fn(pending);
      pending = null;
    }, wait);
  };
}

/**
 * 롤 창 오른쪽에 붙인다. 오른쪽 공간이 모자라면 왼쪽으로 넘긴다.
 * 인자로 받는 clientRect 는 물리 픽셀이므로 DIP 로 바꿔서 계산한다.
 */
function dockBeside(home: BrowserWindow, clientRect: Rect) {
  // Win32 는 물리 픽셀, Electron 은 DIP 를 쓴다. 배율 100% 가 아니면 이 변환이 없으면 엉뚱한 곳으로 간다.
  const client = screen.screenToDipRect(home, clientRect);
  const width = home.getBounds().width;
  const right = client.x + client.width + GAP;

  home.setBounds({
    x: Math.round(right),
    y: Math.round(client.y),
    width,
    height: Math.round(client.height),
  });
}

/** 롤 창이 뜰 때까지 기다렸다가 한 번 붙인다. 새로 부르면 이전 시도는 취소한다. */
function dockWhenClientAppears(home: BrowserWindow) {
  if (findTimer) clearInterval(findTimer);
  const startedAt = Date.now();

  findTimer = setInterval(() => {
    if (home.isDestroyed() || Date.now() - startedAt > FIND_TIMEOUT_MS) {
      clearInterval(findTimer!);
      findTimer = null;
      return;
    }

    const rect = findWindowRect(LOL_CLIENT_TITLE);
    if (!rect) return;

    clearInterval(findTimer!);
    findTimer = null;
    dockBeside(home, rect);
  }, FIND_INTERVAL_MS);
}

let isAttached = false;

/** 홈 창을 롤 클라이언트 창에 붙여 함께 움직이게 한다. */
export function attachHomeToClient(home: BrowserWindow) {
  // 라이브러리가 두 번 초기화되면 예외를 던진다 (activate 로 창을 다시 만드는 경우 대비).
  if (isAttached) return;

  const controller = loadOverlayController();
  if (!controller) return;

  isAttached = true;

  const reposition = throttle(THROTTLE_MS, (rect) => {
    if (home.isDestroyed()) return;
    dockBeside(home, rect);
  });

  controller.events.on('attach', (event) => {
    console.log('[도킹] 클라이언트에 붙음');
    reposition(event);
  });

  controller.events.on('moveresize', reposition);

  const dockSoon = () => dockWhenClientAppears(home);

  controller.events.on('detach', () => {
    console.log('[도킹] 클라이언트가 사라짐 — 다시 뜨면 붙인다');
    dockSoon();
  });

  // 롤 클라이언트가 커졌을 때 (앱을 켰을 때 이미 켜져 있던 경우 포함)
  onStatusChange((status) => {
    if (status === 'connected') dockSoon();
  });

  // 게임이 끝나 클라이언트 창이 다시 뜰 때
  let wasInGame = false;
  onPhaseChange((phase) => {
    if (phase === 'InProgress') {
      wasInGame = true;
    } else if (wasInGame) {
      wasInGame = false;
      dockSoon();
    }
  });

  // 창을 넘기지 않으면(undefined) 라이브러리가 창을 건드리지 않고 좌표만 알려준다.
  // 창을 넘기면 클릭 통과·항상 위·강제 배치가 걸려서 일반 창에는 쓸 수 없다.
  controller.attachByTitle(undefined, LOL_CLIENT_TITLE);
}
