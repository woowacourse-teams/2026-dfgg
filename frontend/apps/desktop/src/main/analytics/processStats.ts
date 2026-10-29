import os from 'node:os';
import { reportError } from '../sentry';

// app.getAppMetrics() 는 Electron 이 띄운 프로세스만 센다. spawn 한 ffmpeg 는 빠져서 녹화 비용이 안 보인다.
// 그래서 ffmpeg 는 PID 로 Windows API 를 직접 불러 잰다.
// (pidusage 는 Windows 에서 wmic 을 쓰는데, 최신 Windows 11 에는 wmic 이 없다.)

const PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
// FILETIME 은 100ns 단위
const FILETIME_PER_MS = 10_000;

function loadKernel32() {
  if (process.platform !== 'win32') return null;
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const koffi = require('koffi') as typeof import('koffi');
    const kernel32 = koffi.load('kernel32.dll');
    const counters = koffi.struct('PROCESS_MEMORY_COUNTERS', {
      cb: 'uint32',
      PageFaultCount: 'uint32',
      PeakWorkingSetSize: 'size_t',
      WorkingSetSize: 'size_t',
      QuotaPeakPagedPoolUsage: 'size_t',
      QuotaPagedPoolUsage: 'size_t',
      QuotaPeakNonPagedPoolUsage: 'size_t',
      QuotaNonPagedPoolUsage: 'size_t',
      PagefileUsage: 'size_t',
      PeakPagefileUsage: 'size_t',
    });

    return {
      countersSize: koffi.sizeof(counters),
      OpenProcess: kernel32.func(
        'void* __stdcall OpenProcess(uint32_t access, bool inherit, uint32_t pid)',
      ),
      CloseHandle: kernel32.func('bool __stdcall CloseHandle(void *handle)'),
      // FILETIME(8바이트)을 uint64 로 받는다. 출력 값은 길이 1인 배열로 돌려받는다.
      GetProcessTimes: kernel32.func(
        'bool __stdcall GetProcessTimes(void *handle, _Out_ uint64_t *creation, _Out_ uint64_t *exit, _Out_ uint64_t *kernel, _Out_ uint64_t *user)',
      ),
      GetProcessMemoryInfo: kernel32.func(
        'bool __stdcall K32GetProcessMemoryInfo(void *handle, _Inout_ PROCESS_MEMORY_COUNTERS *counters, uint32_t cb)',
      ),
    };
  } catch (error) {
    reportError('process-stats-koffi', error);
    return null;
  }
}

// 처음 잴 때 한 번만 불러온다. 실패해도 다시 시도하지 않는다.
let kernel32: ReturnType<typeof loadKernel32> | undefined;

type Reading = { pid: number; cpuTime: number; at: number };

/**
 * 다른 프로세스 하나의 CPU·메모리를 잰다. 같은 PID 를 두 번 이상 재야 CPU 사용률이 나온다.
 * 프로세스가 없거나 못 읽으면 null.
 */
export class ProcessSampler {
  private prev: Reading | null = null;

  sample(pid: number): { cpu?: number; memMb: number } | null {
    if (kernel32 === undefined) kernel32 = loadKernel32();
    if (!kernel32) return null;

    const handle = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
    if (!handle) {
      this.prev = null;
      return null;
    }

    try {
      const creation = [0];
      const exit = [0];
      const kernel = [0];
      const user = [0];
      const counters = { cb: kernel32.countersSize } as { cb: number; WorkingSetSize?: number };

      if (!kernel32.GetProcessTimes(handle, creation, exit, kernel, user)) return null;
      if (!kernel32.GetProcessMemoryInfo(handle, counters, kernel32.countersSize)) return null;

      const now: Reading = { pid, cpuTime: Number(kernel[0]) + Number(user[0]), at: Date.now() };
      const memMb = Number(counters.WorkingSetSize ?? 0) / 1024 ** 2;

      // 직전에 같은 프로세스를 쟀으면 그 사이 CPU 사용률(전체 코어 대비 %)을 구한다.
      let cpu: number | undefined;
      const prev = this.prev;
      if (prev && prev.pid === pid && now.at > prev.at) {
        const usedMs = (now.cpuTime - prev.cpuTime) / FILETIME_PER_MS;
        cpu = (usedMs / ((now.at - prev.at) * os.cpus().length)) * 100;
      }
      this.prev = now;
      return { cpu, memMb };
    } finally {
      kernel32.CloseHandle(handle);
    }
  }
}
