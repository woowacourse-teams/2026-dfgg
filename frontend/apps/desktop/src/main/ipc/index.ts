import { BrowserWindow, ipcMain } from 'electron';
import { fetchCurrentSummoner } from '../lcu/service';
import { getLcuState } from '../lcu/state';
import { EXPANDED_WIDTH, EXPANDED_HEIGHT, COLLAPSED_HEIGHT, COLLAPSED_WIDTH } from '../constants';

// ipc로 보내기
export function registerIpcHandlers() {
  ipcMain.handle('lcu:current-summoner', fetchCurrentSummoner);
  ipcMain.handle('lcu:get-state', getLcuState);
  ipcMain.on('window:set-collapsed', (event, collapsed: boolean) => {
    const win = BrowserWindow.fromWebContents(event.sender);
    if (!win) return;
    const { width, height } = collapsed
      ? { width: COLLAPSED_WIDTH, height: COLLAPSED_HEIGHT }
      : { width: EXPANDED_WIDTH, height: EXPANDED_HEIGHT };

    win.setResizable(true);
    win.setSize(width, height);
    win.setResizable(!collapsed);
  });
}
