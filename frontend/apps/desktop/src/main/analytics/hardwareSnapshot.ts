import os from 'node:os';
import { app } from 'electron';
import { reportError } from '../sentry';

type GpuDevice = {
  vendorId: number;
  deviceId: number;
  active?: boolean;
  deviceString?: string;
  driverVersion?: string;
};

// 녹화 인코더(nvenc / amf / qsv)가 GPU 제조사를 따라가서, 녹화 성능은 제조사별로 비교한다.
const GPU_VENDORS: Record<number, string> = {
  0x10de: 'nvidia',
  0x1002: 'amd',
  0x8086: 'intel',
  0x1414: 'microsoft', // 기본 디스플레이 드라이버 (그래픽 드라이버가 없는 PC)
};

const hex = (n: number) => `0x${n.toString(16)}`;

// getGPUInfo 는 비동기라 앱 시작 때 한 번 읽어 두고, 스냅샷에서는 읽어 둔 값을 쓴다.
let gpu: { gpuVendor?: string; gpuDevice?: string; gpuDriver?: string } = {};

/** main.ts 의 whenReady 에서 한 번 부른다. 실패하면 GPU 정보 없이 보낸다. */
export async function loadGpuInfo() {
  try {
    const info = (await app.getGPUInfo('basic')) as { gpuDevice?: GpuDevice[] };
    // 노트북처럼 GPU 가 둘이면 실제로 쓰는 쪽
    const device = info.gpuDevice?.find((d) => d.active) ?? info.gpuDevice?.[0];
    if (!device) return;
    gpu = {
      gpuVendor: GPU_VENDORS[device.vendorId] ?? hex(device.vendorId),
      gpuDevice: device.deviceString || hex(device.deviceId),
      gpuDriver: device.driverVersion,
    };
  } catch (error) {
    reportError('gpu-info', error);
  }
}

export function hardwareSnapshot() {
  return {
    cpuModel: os.cpus()[0]?.model.trim(),
    cpuCores: os.cpus().length,
    totalMemGb: Math.round(os.totalmem() / 1024 ** 3),
    ...gpu,
  };
}
