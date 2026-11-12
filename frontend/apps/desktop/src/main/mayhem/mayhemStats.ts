/**
 * 아수라장 증강 인식이 한 판 동안 어떻게 돌았는지 모아 두었다가, 판이 끝나면 한 번에 보낸다.
 * trackEvent 는 우마미와 포스트호그로 함께 간다. 확인·읽기마다 보내면 이벤트가 너무 많아 판 단위로 묶는다.
 */
import { trackEvent } from '../analytics/umami';

export type ReadKind = 'picked' | 'low' | 'close' | 'empty' | 'unreadable';

type Stats = {
  championId: number;
  augments: number;
  candidates: number;
  checks: number;
  checkMs: number;
  checkMaxMs: number;
  grabMs: number;
  lumaMs: number;
  reads: number;
  readMs: number;
  readMaxMs: number;
  kinds: Record<ReadKind, number>;
  selections: number; // 카드 묶음이 뜬 횟수
  labeled: number; // 그중 라벨을 하나라도 띄운 횟수
  labelMs: number[]; // 카드 발견 → 첫 라벨까지
};

let stats: Stats | null = null;

const sum = (values: number[]) => values.reduce((a, b) => a + b, 0);
const round = (value: number) => Math.round(value);

/** 판 시작: 증강 창을 열고 카드 읽기를 시작할 때 */
export function beginMayhemStats(championId: number, augments: number, candidates: number) {
  stats = {
    championId,
    augments,
    candidates,
    checks: 0,
    checkMs: 0,
    checkMaxMs: 0,
    grabMs: 0,
    lumaMs: 0,
    reads: 0,
    readMs: 0,
    readMaxMs: 0,
    kinds: { picked: 0, low: 0, close: 0, empty: 0, unreadable: 0 },
    selections: 0,
    labeled: 0,
    labelMs: [],
  };
}

/** 확인 한 번 (찍기 + 카드 찾기) */
export function recordCheck(total: number, grab: number, luma: number) {
  if (!stats) return;
  stats.checks += 1;
  stats.checkMs += total;
  stats.checkMaxMs = Math.max(stats.checkMaxMs, total);
  stats.grabMs += grab;
  stats.lumaMs += luma;
}

/** 카드 한 장 읽기에 걸린 시간 (제목 찍기 + 판정) */
export function recordReadTime(ms: number) {
  if (!stats) return;
  stats.reads += 1;
  stats.readMs += ms;
  stats.readMaxMs = Math.max(stats.readMaxMs, ms);
}

/** 읽은 카드의 판정 결과. 제목을 못 읽었으면 unreadable */
export function recordKind(kind: ReadKind) {
  if (stats) stats.kinds[kind] += 1;
}

/** 카드 묶음 하나가 끝났을 때. 라벨을 띄웠으면 발견부터 첫 라벨까지 걸린 시간을 준다 */
export function recordSelection(labelMs: number | null) {
  if (!stats) return;
  stats.selections += 1;
  if (labelMs !== null) {
    stats.labeled += 1;
    stats.labelMs.push(labelMs);
  }
}

/** 판 끝: 모은 값을 이벤트 하나로 보낸다 */
export function finishMayhemStats() {
  if (!stats) return;
  const s = stats;
  stats = null;

  const sorted = [...s.labelMs].sort((a, b) => a - b);
  trackEvent('desktop-mayhem-game', {
    championId: s.championId,
    augments: s.augments,
    candidates: s.candidates,
    checks: s.checks,
    checkAvgMs: s.checks ? round(s.checkMs / s.checks) : undefined,
    checkMaxMs: round(s.checkMaxMs),
    grabAvgMs: s.checks ? round(s.grabMs / s.checks) : undefined,
    lumaAvgMs: s.checks ? round(s.lumaMs / s.checks) : undefined,
    reads: s.reads,
    readAvgMs: s.reads ? round(s.readMs / s.reads) : undefined,
    readMaxMs: round(s.readMaxMs),
    picked: s.kinds.picked,
    low: s.kinds.low,
    close: s.kinds.close,
    unreadable: s.kinds.unreadable,
    selections: s.selections,
    labeled: s.labeled,
    labelAvgMs: sorted.length ? round(sum(sorted) / sorted.length) : undefined,
    labelMedianMs: sorted.length ? round(sorted[Math.floor(sorted.length / 2)]) : undefined,
    labelMaxMs: sorted.length ? round(sorted[sorted.length - 1]) : undefined,
  });
}
