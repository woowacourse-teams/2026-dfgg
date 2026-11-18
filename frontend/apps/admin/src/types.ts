// server/index.js 가 내려주는 /admin-api/summary 응답 모양

export type Source<T> = { ok: true; data: T } | { ok: false; setup: boolean; error: string };

export interface Point {
  x: string;
  y: number;
}

export interface WebTotals {
  pageviews: number;
  visitors: number;
  visits: number;
  bounces: number;
  totaltime: number;
}

export interface DesktopTotals {
  events: number;
  visitors: number;
  visits: number;
}

export interface DailyRow {
  date: string;
  pageviews: number;
  sessions: number;
  desktopEvents: number;
  launches: number;
}

export type Platform = 'web' | 'desktop';

export interface EventCount {
  name: string;
  count: number;
  platform: Platform;
}

export interface Rate {
  success: number;
  fail: number;
}

export interface AnalyticsData {
  web: WebTotals & { prev: WebTotals };
  desktop: DesktopTotals & { prev: DesktopTotals };
  daily: DailyRow[];
  events: EventCount[];
  rates: Record<Platform, Rate>;
  top: { referrers: Point[]; paths: Point[]; countries: Point[] };
}

export interface StoreSeries {
  total: number;
  prevTotal: number;
  daily: { date: string; count: number }[];
  freshness?: string;
  // CSV 가 주별이면 date 는 그 주의 월요일이고, total 은 기간에 겹치는 비율로 나눈 근사치다.
  granularity?: 'day' | 'week';
}

export interface StoreFunnel {
  pageviews?: number;
  attempts?: number;
  installs?: number;
  firstLaunch?: number;
}

export type OrError<T> = T | { error: string };

export interface StoreData {
  acquisitions: OrError<StoreSeries>;
  installs: OrError<StoreSeries>;
  markets: OrError<Point[]>;
  pageviews?: OrError<StoreSeries>;
  // 지리적 분포·퍼널 파일은 날짜가 없어 내려받은 기간 전체 값이다.
  marketsWholePeriod?: boolean;
  funnel?: StoreFunnel | null;
}

export interface FunnelStep {
  key: string;
  label: string;
  unit: 'visitors' | 'acquisitions';
  events: string[];
  value: number | null;
}

export type AnalyticsSource = 'posthog' | 'umami';

export const SOURCE_NAME: Record<AnalyticsSource, string> = { posthog: 'PostHog', umami: 'Umami' };

export interface Summary {
  mock: boolean;
  range: { startAt: number; endAt: number; days: number; timezone: string; generatedAt: number };
  source: AnalyticsSource;
  analytics: Source<AnalyticsData>;
  store: Source<StoreData>;
  funnel: FunnelStep[];
}

export function hasError<T extends object>(
  value: T | { error: string },
): value is { error: string } {
  return 'error' in value;
}

// ── /admin-api/players ─────────────────────────────────

export type GameResult = 'win' | 'lose' | 'unknown' | 'crash' | 'app-quit' | string;

export interface PlayerGame {
  id: string;
  at: string;
  gameId: string | null;
  queue: string | null;
  champion: string | null;
  position: string | null;
  result: GameResult;
  version: string | null;
  overlaySec: number;
  overlayExpandedSec: number;
  overlayCollapsedSec: number;
  collapseToggles: number;
  recommendSuccess: number;
  recommendError: number;
  liveError: number;
  purchases: number;
  followed: number;
  followedTop1: number;
}

export interface Player {
  key: string;
  riotId: string | null;
  level: number | null;
  version: string | null;
  firstAt: string | null;
  lastAt: string | null;
  connects: number;
  location: string | null;
  os: string | null;
  games: PlayerGame[];
  stats: {
    games: number;
    wins: number;
    losses: number;
    overlaySec: number;
    purchases: number;
    followed: number;
    followedTop1: number;
    recommendSuccess: number;
    recommendError: number;
    liveError: number;
    topChampions: { name: string; games: number }[];
  };
}

export interface PlayersResponse {
  mock: boolean;
  source: AnalyticsSource;
  range: { startAt: number; endAt: number; days: number; generatedAt: number };
  result: Source<{ players: Player[]; totals: { connects: number; games: number } }>;
}
