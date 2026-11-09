import { app } from 'electron';
import { autoUpdater } from 'electron-updater';
import { getLcuState, onPhaseChange } from './lcu/state';
import { reportError } from './sentry';
import { trackEvent } from './analytics/umami';

const CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000; // 켜 둔 채로 오래 쓰니 4시간마다 다시 확인한다.
// 게임 흐름 중에 재시작하면 오버레이가 사라지므로 이때는 설치를 미룬다.
const BUSY_PHASES = ['ChampSelect', 'GameStart', 'InProgress', 'Reconnect'];

let updateReady = false;

const isBusy = () => BUSY_PHASES.includes(getLcuState().phase ?? '');

function installIfIdle() {
  if (!updateReady || isBusy()) return;
  trackEvent('desktop-update', { stage: 'install' });
  autoUpdater.quitAndInstall(true, true);
}

export function initAutoUpdate() {
  if (!app.isPackaged || process.windowsStore) return;

  autoUpdater.on('update-available', (info) => {
    trackEvent('desktop-update', { stage: 'available', to: info.version });
  });
  autoUpdater.on('update-downloaded', (info) => {
    trackEvent('desktop-update', { stage: 'downloaded', to: info.version });
    updateReady = true;
    installIfIdle();
  });
  autoUpdater.on('error', (error) => {
    console.error('업데이트 실패', error);
    // 업데이트가 계속 실패하면 옛 버전에 남는 사용자가 생겨서 따로 센다.
    trackEvent('desktop-update', { stage: 'error' });
    reportError('auto-update', error);
  });

  onPhaseChange(() => installIfIdle());

  const check = () => {
    autoUpdater.checkForUpdatesAndNotify().catch((error) => {
      console.error('업데이트 확인 실패', error);
      reportError('auto-update', error);
    });
  };
  check();
  setInterval(check, CHECK_INTERVAL_MS);
}
