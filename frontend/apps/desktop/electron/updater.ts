import { app, BrowserWindow, ipcMain } from 'electron';
import { autoUpdater } from 'electron-updater';

/** 렌더러가 배너를 그리는 데 필요한 만큼만. */
export interface UpdateState {
  /** idle: 조용함 · downloading: 받는 중 · ready: 재시작하면 적용 · error: 실패 */
  status: 'idle' | 'downloading' | 'ready' | 'error';
  /** ready 일 때 새 버전 번호. */
  version: string | null;
  /** downloading 일 때 0~100. */
  percent: number;
}

/** 켜자마자 한 번, 그 뒤로는 이 간격마다 확인한다. 하루 종일 켜두는 사람이 있다. */
const CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000;

/** 시작 직후는 창 띄우기·LCU 연결과 겹치므로 조금 미룬다. */
const FIRST_CHECK_DELAY_MS = 10_000;

let state: UpdateState = { status: 'idle', version: null, percent: 0 };
let timer: ReturnType<typeof setInterval> | undefined;

/**
 * 이 앱을 스스로 갱신하면 안 되는 상황.
 *
 * - 개발 중에는 설치본이 아니라 갱신 대상이 없다.
 * - Microsoft Store(msix) 버전은 스토어가 갱신한다. 여기서 또 받으면
 *   서로 다른 경로로 두 번 설치되어 꼬인다. Electron 이 스토어 실행을
 *   process.windowsStore 로 알려준다.
 */
function shouldSkip(): boolean {
  return !app.isPackaged || process.windowsStore === true;
}

function publish(windows: () => BrowserWindow[], next: Partial<UpdateState>) {
  state = { ...state, ...next };
  for (const window of windows()) {
    if (!window.isDestroyed()) window.webContents.send('update:state', state);
  }
}

/**
 * 자동 업데이트. 조용히 받아두고, 다 받으면 렌더러에 알려 재시작을 권한다.
 *
 * 몰래 재시작시키지 않는다 — 밴픽 중에 앱이 꺼지면 그게 더 큰 사고다.
 */
export function setupAutoUpdate(getWindows: () => BrowserWindow[]) {
  ipcMain.handle('update:getState', () => state);
  ipcMain.handle('update:install', () => {
    if (state.status !== 'ready') return false;
    // isSilent=true, isForceRunAfter=true — 설치 창 없이 끝나고 다시 켜진다.
    autoUpdater.quitAndInstall(true, true);
    return true;
  });

  if (shouldSkip()) {
    console.log('[update] 건너뜀', app.isPackaged ? '(스토어 버전)' : '(개발 모드)');
    return;
  }

  // 받는 것까지만 자동, 설치 시점은 사용자가 정한다.
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;

  autoUpdater.on('update-available', (info) => {
    console.log('[update] 새 버전', info.version);
    publish(getWindows, { status: 'downloading', version: info.version, percent: 0 });
  });

  autoUpdater.on('download-progress', (progress) => {
    publish(getWindows, { percent: Math.round(progress.percent) });
  });

  autoUpdater.on('update-downloaded', (info) => {
    console.log('[update] 준비됨', info.version);
    publish(getWindows, { status: 'ready', version: info.version, percent: 100 });
  });

  autoUpdater.on('error', (error) => {
    // 인터넷이 끊겨도 앱은 그대로 써야 하므로 조용히 넘어간다.
    console.error('[update] 실패', error);
    publish(getWindows, { status: 'error' });
  });

  const check = () => {
    autoUpdater.checkForUpdates().catch((error) => console.error('[update] 확인 실패', error));
  };

  setTimeout(check, FIRST_CHECK_DELAY_MS);
  timer = setInterval(check, CHECK_INTERVAL_MS);
}

export function stopAutoUpdate() {
  if (timer) clearInterval(timer);
  timer = undefined;
}
