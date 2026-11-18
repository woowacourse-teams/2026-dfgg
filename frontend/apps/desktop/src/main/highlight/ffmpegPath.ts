import { app } from 'electron';
import path from 'node:path';

// main에서 ffmpeg 실행 경로
export function getFfmpegPath() {
  const base = app.isPackaged
    ? path.join(process.resourcesPath, 'ffmpeg')
    : path.join(app.getAppPath(), 'resources', 'ffmpeg');
  return path.join(base, 'ffmpeg.exe');
}

// 클립에 겹칠 워터마크(로고 + dfgg.pro) 경로
export function getWatermarkPath() {
  const base = app.isPackaged
    ? path.join(process.resourcesPath, 'icons')
    : path.join(app.getAppPath(), 'resources', 'icons');
  return path.join(base, 'watermark.png');
}
