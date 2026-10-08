import { app } from 'electron';
import path from 'node:path';

export function getFfmePath() {
  const base = app.isPackaged
    ? path.join(process.resourcesPath, 'ffmpeg')
    : path.join(app.getAppPath(), 'resources', 'ffmpeg');

  return path.join(base, 'ffmpeg.exe');
}
