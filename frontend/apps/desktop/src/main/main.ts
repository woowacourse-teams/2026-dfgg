import { app, BrowserWindow } from 'electron';
import path from 'node:path';
import { registerIpcHandlers } from './ipc';
import { startLcuConnection, stopLcuConnection } from './lcu/connection';
import { initLivePolling, stopLivePolling } from './live/poller';

const DEV_SERVER_URL = 'http://localhost:3001';

// 렌더러는 창 단위(out/renderer/home, out/renderer/overlay ...)로 빌드된다.
function loadRenderer(win: BrowserWindow, name: string) {
  if (app.isPackaged) {
    win.loadFile(path.join(__dirname, `../renderer/${name}/index.html`));
  } else {
    win.loadURL(`${DEV_SERVER_URL}/${name}/index.html`);
    win.webContents.openDevTools();
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 800,
    height: 600,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
    },
  });

  const overlay = new BrowserWindow({
    width: 400,
    height: 300,
    title: 'overlay',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
    },
  });

  loadRenderer(win, 'home');
  loadRenderer(overlay, 'overlay');
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
