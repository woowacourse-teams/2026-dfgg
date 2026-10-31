import { BrowserWindow } from 'electron';
import type { BroadcastChannels } from '../types';

//  모든 창과 페이지에 정보 보내기
export function broadcastToAllWindows<T extends keyof BroadcastChannels>(
  channel: T,
  data: BroadcastChannels[T],
) {
  const allWindows = BrowserWindow.getAllWindows();

  for (const win of allWindows) {
    if (win.isDestroyed() || win.webContents.isDestroyed()) continue;
    win.webContents.send(channel, data);
  }
}
