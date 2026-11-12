import { app, webContents } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import { getLcuState, onPhaseChange } from '../lcu/state';

const LOG = path.join(app.getPath('userData'), 'memory-probe.log');
const mb = (kb: number) => Math.round(kb / 1024);

function snapshot(label: string) {
  // pid → 어떤 창인지 (home, overlay, augments …)
  const owners = new Map<number, string>();
  for (const wc of webContents.getAllWebContents()) {
    const name =
      new URL(wc.getURL() || 'about:blank').pathname.split('/').filter(Boolean).at(-2) ??
      wc.getTitle();
    owners.set(wc.getOSProcessId(), name);
  }

  const rows = app.getAppMetrics().map((m) => ({
    type: m.type, // Browser(=main), Tab(=렌더러), GPU, Utility
    owner: m.type === 'Browser' ? 'main' : (owners.get(m.pid) ?? '-'),
    pid: m.pid,
    privateMB: mb(m.memory.privateBytes ?? 0), // 작업 관리자 '메모리' 열과 가까운 값
    workingSetMB: mb(m.memory.workingSetSize),
  }));
  const total = rows.reduce((s, r) => s + r.privateMB, 0);

  const line =
    `\n[${new Date().toISOString()}] ${label} — total ${total}MB\n` +
    rows
      .map(
        (r) =>
          `  ${r.type.padEnd(8)} ${r.owner.padEnd(10)} pid=${r.pid} private=${r.privateMB}MB ws=${r.workingSetMB}MB`,
      )
      .join('\n');
  console.log(line);
  fs.appendFileSync(LOG, line);
}

export function startMemoryProbe() {
  snapshot('app-start');
  let timer: NodeJS.Timeout | null = null;
  onPhaseChange((phase) => {
    snapshot(`phase:${phase}`);
    if (timer) clearInterval(timer);
    timer = null;
    // 게임 중에는 30초마다 (OCR 워커 초기화·인식 후 변화 보기)
    if (phase === 'InProgress') timer = setInterval(() => snapshot('in-game'), 30_000);
  });
  void getLcuState;
}
