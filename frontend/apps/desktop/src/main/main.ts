// 다른 모듈이 로드되기 전에 초기화해야 그 안에서 난 에러도 잡힌다.
import './sentry';

import { app, BrowserWindow } from 'electron';
import path from 'node:path';
import { registerIpcHandlers } from './ipc';
import { startLcuConnection, stopLcuConnection } from './lcu/connection';
import { initLivePolling, stopLivePolling } from './live/poller';
import { EXPANDED_WIDTH, EXPANDED_HEIGHT } from './constants';
import { onPhaseChange, onStatusChange } from './lcu/state';
import { ensureBorderlessMode } from './gameConfig/gameConfig';
import { isStartupLaunch, setAutoLaunch } from './autoLaunch/autoLaunch';
import { setTray } from './autoLaunch/tray';
import { attachHomeToClient } from './docking/docking';

import { flushOutbox, trackEvent } from './analytics/umami';
import { readStore, updateStore } from './analytics/store';
import { initIdentity } from './analytics/identity';
import {
  endSessionTracking,
  recoverLastSession,
  startSessionTracking,
} from './analytics/sessionTracker';
import { endGameTracking, initGameTracking, recoverLastGame } from './analytics/gameTracker';
import { initAutoUpdate } from './autoUpdate';
import { detectEncoder } from './highlight/encoder';

const DEV_SERVER_URL = 'http://localhost:3001';

// home 창을 저장하고, 이미 실행되고 있는 같은 프로그램 인스턴스를 확인하는 변수
let homeWindow: BrowserWindow | null = null;
const gotTheLock = app.requestSingleInstanceLock();

// 렌더러는 창 단위(out/renderer/home, out/renderer/overlay ...)로 빌드된다.
function loadRenderer(win: BrowserWindow, name: string) {
  if (app.isPackaged) {
    win.loadFile(path.join(__dirname, `../renderer/${name}/index.html`));
  } else {
    win.loadURL(`${DEV_SERVER_URL}/${name}/index.html`);
  }
}

// InProgress일 때만 오버레이 창을 표시하기
function overlayWithPhase(overlay: BrowserWindow) {
  onPhaseChange((phase) => {
    if (phase === 'InProgress') {
      overlay.showInactive();
    } else {
      overlay.hide();
    }
  });
}

// 연결 감지하면서 connected일 때, 앱 실행해서 home 화면 보이게 하기
function detectClientStatus(home: BrowserWindow) {
  let shown = false;

  onStatusChange((status) => {
    if (status !== 'connected') return;
    if (shown) return;

    home.showInactive();
    shown = true;
  });
}

function createWindow() {
  const home = new BrowserWindow({
    width: 480,
    height: 600,
    show: false,
    titleBarStyle: 'hidden',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
    },
  });

  const overlay = new BrowserWindow({
    width: EXPANDED_WIDTH,
    height: EXPANDED_HEIGHT,
    x: 10,
    y: 300,
    title: 'overlay',
    frame: false,
    transparent: true,
    skipTaskbar: true,
    alwaysOnTop: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
    },
  });

  // home 창을 롤 클라이언트 옆에 붙인다. 네이티브 모듈이 없으면 조용히 건너뛴다.
  attachHomeToClient(home);
  startSessionTracking(home);

  overlay.hide();
  overlayWithPhase(overlay);
  detectClientStatus(home);

  // 앱 실행 시, 전체 화면 모드 수정하는 구독 설정
  onPhaseChange(() => ensureBorderlessMode());

  overlay.setAlwaysOnTop(true, 'screen-saver');

  loadRenderer(home, 'home');
  loadRenderer(overlay, 'overlay');

  home.on('closed', () => {
    app.quit();
  });

  return home;
}

// 첫 번째 or 두 번재 인스턴스인지 확인하기
if (!gotTheLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (!homeWindow) return;
    if (homeWindow.isMinimized()) homeWindow.restore();
    homeWindow.show();
    homeWindow.focus();
  });

  app.whenReady().then(async () => {
    initAutoUpdate();

    const t = Date.now();
    detectEncoder().then((e) => console.log('[encoder]', e, `${Date.now() - t}ms`));

    // 지난 실행에서 못 보낸 이벤트부터 정리해서 보낸다.
    recoverLastSession();
    recoverLastGame();
    flushOutbox();

    // LCU 연결 전에 구독해야 첫 connected 를 놓치지 않는다.
    initIdentity();
    initGameTracking();

    registerIpcHandlers();
    homeWindow = createWindow();

    // 첫 실행이면 설치로 센다. userData 는 삭제해도 남아서 재설치는 세지 않는다.
    if (!readStore().installed) {
      trackEvent('desktop-install');
      updateStore({ installed: true });
    }

    // 앱 시작 시에만 직접 앱 클릭했을 때 화면에 보여주기
    const startupLaunch = isStartupLaunch();
    if (!startupLaunch) homeWindow.show();
    trackEvent('desktop-launch', { startup: startupLaunch });

    setTray(homeWindow);
    setAutoLaunch(true);

    startLcuConnection();
    initLivePolling();

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) {
        createWindow();
      }
    });
  });
}

app.on('will-quit', () => {
  endGameTracking();
  endSessionTracking();
  stopLivePolling();
  stopLcuConnection();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
