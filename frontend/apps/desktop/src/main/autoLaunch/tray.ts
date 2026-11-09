import { BrowserWindow, Menu, Tray, app, nativeImage, shell } from 'electron';
import path from 'node:path';
import { setQuitSource } from '../analytics/sessionTracker';
import { mainStrings } from '../locale';

let tray: Tray | null = null;

// 패키지와 빌드에 따른 아이콘 경로 설정 다르게 하기
function resolveTrayIconPath() {
  return app.isPackaged
    ? path.join(process.resourcesPath, 'icons', 'icon.ico')
    : path.join(app.getAppPath(), 'resources', 'icons', 'icon.ico');
}

function noticePath() {
  return app.isPackaged
    ? path.join(process.resourcesPath, 'ffmpeg', 'NOTICE.txt')
    : path.join(app.getAppPath(), 'resources', 'ffmpeg', 'NOTICE.txt');
}

// 트레이 설정하기
export function setTray(home: BrowserWindow) {
  // app ready 이후에 불리므로 여기서 언어를 정한다.
  const t = mainStrings();

  const iconPath = resolveTrayIconPath();
  const trayIcon = nativeImage.createFromPath(iconPath);

  if (trayIcon.isEmpty()) {
    console.error('트레이 아이콘을 찾지 못했습니다', iconPath);
  }

  tray = new Tray(trayIcon);

  const contextMenu = Menu.buildFromTemplate([
    {
      label: t.tray.open,
      click: () => home.show(),
    },
    { label: t.tray.licenses, click: () => void shell.openPath(noticePath()) },
    { type: 'separator' },
    {
      label: t.tray.quit,
      click: () => {
        setQuitSource('tray');
        app.quit();
      },
    },
  ]);

  tray.setToolTip('DFGG');
  tray.setContextMenu(contextMenu);

  tray.on('click', () => {
    if (home.isVisible()) {
      home.hide();
    } else {
      home.show();
    }
  });
}
