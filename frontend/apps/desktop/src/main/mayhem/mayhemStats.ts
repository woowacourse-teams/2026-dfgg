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
  tieBreaks: number; // picked 중 헷갈림을 다른 부분 재비교로 가른 횟수
  selections: number; // 카드 묶음이 뜬 횟수
  labeled: number; // 그중 라벨을 하나라도 띄운 횟수
  labelMs: number[]; // 카드 발견 → 첫 라벨까지
  gameWidth: number; // 게임 창 크기. 창을 아직 못 찾았으면 0
  gameHeight: number;
  misses: Set<string>; // 이미 보낸 실패 (같은 카드를 다시 읽을 때마다 보내지 않게)
};

// 한 판에 보내는 실패 이벤트 상한
const MAX_MISSES = 30;

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
    tieBreaks: 0,
    selections: 0,
    labeled: 0,
    labelMs: [],
    gameWidth: 0,
    gameHeight: 0,
    misses: new Set(),
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

/** 게임 창 크기. 해상도별로 실패를 나눠 보려고 남긴다 */
export function recordScreen(width: number, height: number) {
  if (!stats) return;
  stats.gameWidth = width;
  stats.gameHeight = height;
}

type MissTop = { candidate: { augmentId: number; name: string; rarity?: string }; score: number };

/**
 * 못 고른 판정(low·close) 하나를 이벤트로 보낸다. 어떤 증강에서 실패가 나는지 보기 위한 것.
 * 같은 판에서 같은 결과(종류 + 1·2등)는 한 번만 보낸다.
 */
export function recordMiss(kind: 'low' | 'close', top: MissTop[], titleWidth: number) {
  if (!stats || stats.misses.size >= MAX_MISSES) return;
  const [first, second] = top;
  const key = `${kind}:${first?.candidate.augmentId}:${second?.candidate.augmentId}`;
  if (stats.misses.has(key)) return;
  stats.misses.add(key);

  const score = (value: number | undefined) =>
    value === undefined ? undefined : Math.round(value * 1000) / 1000;
  trackEvent('desktop-mayhem-miss', {
    kind,
    championId: stats.championId,
    firstId: first?.candidate.augmentId,
    firstName: first?.candidate.name,
    firstScore: score(first?.score),
    secondId: second?.candidate.augmentId,
    secondName: second?.candidate.name,
    secondScore: score(second?.score),
    lead: first && second ? score(first.score - second.score) : undefined,
    titleWidth, // 화면 제목 마스크 폭 (높이 32 기준). 글자 수를 가늠할 수 있다
    gameWidth: stats.gameWidth || undefined,
    gameHeight: stats.gameHeight || undefined,
  });
}

/** 헷갈림(close)이었지만 다른 부분만 다시 비교해 고른 경우. kind 는 picked 로 따로 센다 */
export function recordTieBreak() {
  if (stats) stats.tieBreaks += 1;
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
    tieBreaks: s.tieBreaks,
    selections: s.selections,
    labeled: s.labeled,
    labelAvgMs: sorted.length ? round(sum(sorted) / sorted.length) : undefined,
    labelMedianMs: sorted.length ? round(sorted[Math.floor(sorted.length / 2)]) : undefined,
    labelMaxMs: sorted.length ? round(sorted[sorted.length - 1]) : undefined,
    gameWidth: s.gameWidth || undefined,
    gameHeight: s.gameHeight || undefined,
  });
}
