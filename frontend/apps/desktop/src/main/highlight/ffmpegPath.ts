import { app } from 'electron';
import path from 'node:path';

// main에서 ffmpeg 실행 경로
export function getFfmpegPath() {
  const base = app.isPackaged
    ? path.join(process.resourcesPath, 'ffmpeg')
    : path.join(app.getAppPath(), 'resources', 'ffmpeg');
  return path.join(base, 'ffmpeg.exe');
}
