import { BrowserWindow, Menu, Tray, app, nativeImage } from 'electron';
import path from 'node:path';

let tray: Tray | null = null;

// 패키지와 빌드에 따른 아이콘 경로 설정 다르게 하기
function resolveTrayIconPath() {
  return app.isPackaged
    ? path.join(process.resourcesPath, 'icons', 'icon.ico')
    : path.join(app.getAppPath(), 'resources', 'icons', 'icon.ico');
}

// 트레이 설정하기
export function setTray(home: BrowserWindow) {
  const iconPath = resolveTrayIconPath();
  const trayIcon = nativeImage.createFromPath(iconPath);

  if (trayIcon.isEmpty()) {
    console.error('트레이 아이콘을 찾지 못했습니다', iconPath);
  }

  tray = new Tray(trayIcon);

  const contextMenu = Menu.buildFromTemplate([
    {
      label: '열기',
      click: () => home.show(),
    },
    {
      label: '종료',
      click: () => app.quit(),
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
