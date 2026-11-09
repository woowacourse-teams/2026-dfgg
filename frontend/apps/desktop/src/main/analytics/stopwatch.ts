// 켜고 끈 구간만 누적하는 스톱워치
export class Stopwatch {
  private totalMs = 0;
  private startedAt: number | null = null;

  start() {
    if (this.startedAt === null) this.startedAt = Date.now();
  }

  stop() {
    if (this.startedAt === null) return;
    this.totalMs += Date.now() - this.startedAt;
    this.startedAt = null;
  }

  seconds() {
    const runningMs = this.startedAt === null ? 0 : Date.now() - this.startedAt;
    return Math.round((this.totalMs + runningMs) / 1000);
  }
}
