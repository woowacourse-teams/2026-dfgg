// main/analytics/hardwareSnapshot.ts (새 파일)
import os from 'node:os';

export function hardwareSnapshot() {
  return {
    cpuModel: os.cpus()[0]?.model.trim(),
    cpuCores: os.cpus().length,
    totalMemGb: Math.round(os.totalmem() / 1024 ** 3),
  };
}
