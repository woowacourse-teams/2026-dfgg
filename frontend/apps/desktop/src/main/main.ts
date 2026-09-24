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

    home.show();
    shown = true;
  });
}

function createWindow() {
  const home = new BrowserWindow({
    width: 800,
    height: 600,
    show: false,
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
    registerIpcHandlers();
    homeWindow = createWindow();

    // 앱 시작 시에만 직접 앱 클릭했을 때 화면에 보여주기
    if (!isStartupLaunch()) homeWindow.show();

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
  stopLivePolling();
  stopLcuConnection();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
