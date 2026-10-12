import type { MatchSummary } from '../../../shared/types';
import { QUEUE_FILTERS, queueOf } from './queues';

const MINUTE_SECONDS = 60;
const MINUTE_MS = 60 * 1000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;
const RECENT_DAYS = 30;

/** '18분'. 초까지 적으면 숫자가 늘 뿐 읽는 데 도움이 안 된다. */
export const formatMinutes = (seconds: number): string =>
  `${Math.round(seconds / MINUTE_SECONDS)}분`;

/** '19,700'. 자리가 넉넉한 곳에서는 줄이지 않고 그대로 적는 편이 읽기 쉽다. */
export const formatNumber = (value: number): string => value.toLocaleString('ko-KR');

/** 최근 판은 '3시간 전', 오래된 판은 날짜로 보여준다. */
export function formatWhen(iso: string): string {
  const played = new Date(iso);
  const diff = Date.now() - played.getTime();

  if (diff < HOUR_MS) return `${Math.max(1, Math.floor(diff / MINUTE_MS))}분 전`;
  if (diff < DAY_MS) return `${Math.floor(diff / HOUR_MS)}시간 전`;
  if (diff < RECENT_DAYS * DAY_MS) return `${Math.floor(diff / DAY_MS)}일 전`;

  return played.toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric' });
}

/** 탭과 같은 짧은 이름을 쓴다. 클라이언트가 주는 이름('솔로 랭크 게임')은 한 줄에 넣기엔 길다. */
export function queueLabel(match: MatchSummary): string {
  if (match.isCustom) return '사용자 설정';
  const queue = queueOf(match);
  return queue === 'etc'
    ? match.queueName
    : (QUEUE_FILTERS.find(({ key }) => key === queue)?.label ?? match.queueName);
}

/** 분당 CS. 게임이 짧으면 의미가 없어 0으로 접는다. */
export const csPerMin = (cs: number, seconds: number): string =>
  seconds < MINUTE_SECONDS ? '0.0' : (cs / (seconds / MINUTE_SECONDS)).toFixed(1);

/** 분당 값을 정수로. 게임이 1분도 안 됐으면 0. */
export const perMinute = (value: number, seconds: number): string =>
  seconds < MINUTE_SECONDS ? '0' : formatNumber(Math.round(value / (seconds / MINUTE_SECONDS)));

/** 전체 중 차지하는 몫을 0~100 정수로. 전체가 0이면 0. */
export const percentOf = (part: number, whole: number): number =>
  whole > 0 ? Math.round((part / whole) * 100) : 0;
