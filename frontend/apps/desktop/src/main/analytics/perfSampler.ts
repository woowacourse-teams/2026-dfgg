import os from 'node:os';
import { app } from 'electron';

// 재는 것 자체가 부담이 되지 않도록 10초마다만 잰다.
const SAMPLE_MS = 10_000;

type CpuTimes = { idle: number; total: number };

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
  private freeMemMinMb = Infinity;

  /** isRecording: 지금 하이라이트를 녹화 중인지. 게임 중간에 켜고 끌 수 있어서 잴 때마다 묻는다. */
  start(isRecording: () => boolean) {
    this.prevCpu = readCpu();
    this.timer = setInterval(() => this.sample(isRecording()), SAMPLE_MS);
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  private sample(recording: boolean) {
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
    // percentCPUUsage 는 코어 하나 기준이라, 코어 수로 나눠 전체 대비 %로 맞춘다.
    const appCpu = metrics.reduce((sum, m) => sum + m.cpu.percentCPUUsage, 0) / os.cpus().length;
    const appMemMb = metrics.reduce((sum, m) => sum + m.memory.workingSetSize, 0) / 1024;
    this.appCpuSum += appCpu;
    this.appCpuMax = Math.max(this.appCpuMax, appCpu);
    this.appMemMaxMb = Math.max(this.appMemMaxMb, appMemMb);

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
      freeMemMinMb: Math.round(this.freeMemMinMb),
    };
  }
}
