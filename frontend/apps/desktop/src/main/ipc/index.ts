import { ipcMain } from 'electron';
import { fetchCurrentSummoner } from '../lcu/service';
import { getLcuState } from '../lcu/state';

// ipc로 보내기
export function registerIpcHandlers() {
  ipcMain.handle('lcu:current-summoner', fetchCurrentSummoner);
  ipcMain.handle('lcu:get-state', getLcuState);
}
