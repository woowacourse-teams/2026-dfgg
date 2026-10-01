import { app } from 'electron';

const STARTUP_FLAG = '--startup';

// 이번 실행을 누가 시작했나 — Windows인가 사용자인가
export const isStartupLaunch = () => process.argv.includes(STARTUP_FLAG);

// 지금 등록돼 있나 (상태 읽기)
export function isAutoLaunchEnabled() {
  if (!app.isPackaged || process.windowsStore) return false;
  return app.getLoginItemSettings().openAtLogin;
}

// 등록하거나 해제 (상태 쓰기)
export function setAutoLaunch(enabled: boolean) {
  if (!app.isPackaged || process.windowsStore) return false;

  app.setLoginItemSettings({
    openAtLogin: enabled,
    args: [STARTUP_FLAG],
  });
}
