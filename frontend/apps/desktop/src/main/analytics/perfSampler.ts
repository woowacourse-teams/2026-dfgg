import os from 'node:os';
import { app } from 'electron';
import { ProcessSampler } from './processStats';

// 재는 것 자체가 부담이 되지 않도록 10초마다만 잰다.
const SAMPLE_MS = 10_000;

type CpuTimes = { idle: number; total: number };

// 메모리를 따로 볼 프로세스 종류. 그 밖의 종류(Utility 등)는 appMemMaxMb 합계에만 들어간다.
const MEM_TYPES = { Browser: 'appMemMainMaxMb', Tab: 'appMemRendererMaxMb', GPU: 'appMemGpuMaxMb' };
type MemKey = (typeof MEM_TYPES)[keyof typeof MEM_TYPES];

// 모든 코어의 누적 시간. 두 시점의 차이로 그 사이 사용률을 구한다.
function readCpu(): CpuTimes {
  let idle = 0;
  let total = 0;
  for (const { times } of os.cpus()) {
    idle += times.idle;
    total += times.user + times.nice + times.sys + times.idle + times.irq;
  }
  return { idle, total };
}

const toMb = (kb: number) => kb / 1024;

/** 지금 DFGG 앱(메인 + 렌더러 + GPU 프로세스 합)이 쓰는 메모리. 세션 스냅샷에서도 쓴다. */
export function appMemoryMb() {
  return Math.round(app.getAppMetrics().reduce((sum, m) => sum + toMb(m.memory.workingSetSize), 0));
}

/** 한 판 동안 PC 부하를 재서 평균·최대로 요약한다. */
export class PerfSampler {
  private timer: NodeJS.Timeout | null = null;
  private prevCpu: CpuTimes | null = null;
  private samples = 0;
  private recordingSamples = 0;
  private cpuSum = 0;
  private cpuMax = 0;
  private appCpuSum = 0;
  private appCpuMax = 0;
  private appMemMaxMb = 0;
  private memByType: Record<MemKey, number> = {
    appMemMainMaxMb: 0,
    appMemRendererMaxMb: 0,
    appMemGpuMaxMb: 0,
  };
  private freeMemMinMb = Infinity;
  // 녹화 프로세스(ffmpeg). getAppMetrics 에 안 잡혀서 따로 잰다.
  private ffmpeg = new ProcessSampler();
  private ffmpegSamples = 0;
  private ffmpegCpuSum = 0;
  private ffmpegCpuMax = 0;
  private ffmpegMemMaxMb = 0;

  /**
   * isRecording: 지금 하이라이트를 녹화 중인지. 게임 중간에 켜고 끌 수 있어서 잴 때마다 묻는다.
   * recorderPid: 녹화 중인 ffmpeg 의 PID. 녹화 중이 아니면 null.
   */
  start(isRecording: () => boolean, recorderPid: () => number | null) {
    this.prevCpu = readCpu();
    this.timer = setInterval(() => this.sample(isRecording(), recorderPid()), SAMPLE_MS);
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  private sample(recording: boolean, pid: number | null) {
    // 시스템 전체 CPU (%)
    const cpu = readCpu();
    if (this.prevCpu) {
      const total = cpu.total - this.prevCpu.total;
      const idle = cpu.idle - this.prevCpu.idle;
      const usage = total > 0 ? (1 - idle / total) * 100 : 0;
      this.cpuSum += usage;
      this.cpuMax = Math.max(this.cpuMax, usage);
    }
    this.prevCpu = cpu;

    // DFGG 앱 (메인 + 렌더러 + GPU 프로세스 합)
    const metrics = app.getAppMetrics();
    // percentCPUUsage 는 Electron 이 이미 코어 수로 나눈 전체 대비 % 다. 다시 나누지 않는다.
    // (2.1.1 까지는 한 번 더 나눠서 실제보다 코어 수만큼 작게 기록됐다.)
    const appCpu = metrics.reduce((sum, m) => sum + m.cpu.percentCPUUsage, 0);
    const appMemMb = metrics.reduce((sum, m) => sum + toMb(m.memory.workingSetSize), 0);
    this.appCpuSum += appCpu;
    this.appCpuMax = Math.max(this.appCpuMax, appCpu);
    this.appMemMaxMb = Math.max(this.appMemMaxMb, appMemMb);

    // 어느 프로세스에서 메모리가 늘었는지 보려고 종류별로도 남긴다. (렌더러는 창마다 하나라 합친다.)
    const byType: Partial<Record<MemKey, number>> = {};
    for (const m of metrics) {
      const key = MEM_TYPES[m.type as keyof typeof MEM_TYPES];
      if (key) byType[key] = (byType[key] ?? 0) + toMb(m.memory.workingSetSize);
    }
    for (const [key, mb] of Object.entries(byType) as [MemKey, number][]) {
      this.memByType[key] = Math.max(this.memByType[key], mb);
    }

    // 녹화 중이면 ffmpeg 도 잰다.
    const ffmpeg = pid !== null ? this.ffmpeg.sample(pid) : null;
    if (ffmpeg) {
      this.ffmpegMemMaxMb = Math.max(this.ffmpegMemMaxMb, ffmpeg.memMb);
      // CPU 는 같은 PID 를 두 번째 잴 때부터 나온다.
      if (ffmpeg.cpu !== undefined) {
        this.ffmpegSamples++;
        this.ffmpegCpuSum += ffmpeg.cpu;
        this.ffmpegCpuMax = Math.max(this.ffmpegCpuMax, ffmpeg.cpu);
      }
    }

    // 남은 메모리
    this.freeMemMinMb = Math.min(this.freeMemMinMb, os.freemem() / 1024 ** 2);

    this.samples++;
    if (recording) this.recordingSamples++;
  }

  /** desktop-game-end 에 붙일 값. 한 번도 못 쟀으면 빈 객체. */
  summary() {
    if (this.samples === 0) return {};
    const round = (n: number) => Math.round(n * 10) / 10;
    return {
      perfSamples: this.samples,
      // 녹화한 시간 비율. 1이면 판 내내 녹화, 0이면 녹화 안 함
      recordingRatio: round(this.recordingSamples / this.samples),
      cpuAvg: round(this.cpuSum / this.samples),
      cpuMax: round(this.cpuMax),
      appCpuAvg: round(this.appCpuSum / this.samples),
      appCpuMax: round(this.appCpuMax),
      appMemMaxMb: Math.round(this.appMemMaxMb),
      ...Object.fromEntries(Object.entries(this.memByType).map(([k, v]) => [k, Math.round(v)])),
      freeMemMinMb: Math.round(this.freeMemMinMb),
      // 녹화를 안 했거나 못 쟀으면 ffmpeg 값은 보내지 않는다.
      ...(this.ffmpegSamples > 0 && {
        ffmpegCpuAvg: round(this.ffmpegCpuSum / this.ffmpegSamples),
        ffmpegCpuMax: round(this.ffmpegCpuMax),
      }),
      ...(this.ffmpegMemMaxMb > 0 && { ffmpegMemMaxMb: Math.round(this.ffmpegMemMaxMb) }),
    };
  }
}
