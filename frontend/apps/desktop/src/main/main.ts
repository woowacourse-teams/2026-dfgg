import { app, BrowserWindow } from 'electron';
import path from 'node:path';
import { registerIpcHandlers } from './ipc';
import { startLcuConnection, stopLcuConnection } from './lcu/connection';
import { initLivePolling, stopLivePolling } from './live/poller';
import { EXPANDED_WIDTH, EXPANDED_HEIGHT } from './constants';
import { onPhaseChange } from './lcu/state';

const DEV_SERVER_URL = 'http://localhost:3001';

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

function createWindow() {
  const home = new BrowserWindow({
    width: 800,
    height: 600,
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

  onPhaseChange((phase) => {
    if (phase === 'InProgress') {
      overlay.showInactive();
    } else {
      overlay.hide();
    }
  });

  overlay.setAlwaysOnTop(true, 'screen-saver');

  loadRenderer(home, 'home');
  loadRenderer(overlay, 'overlay');

  home.on('closed', () => {
    app.quit();
  });
}

app.whenReady().then(async () => {
  registerIpcHandlers();
  createWindow();
  startLcuConnection();
  initLivePolling();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('will-quit', () => {
  stopLivePolling();
  stopLcuConnection();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
