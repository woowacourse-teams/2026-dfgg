const integer = new Intl.NumberFormat('ko-KR');

export const formatNumber = (value: number) => integer.format(Math.round(value));

export const formatPercent = (value: number) =>
  `${(value * 100).toFixed(value < 0.1 && value > 0 ? 1 : 0)}%`;

export function formatDuration(seconds: number) {
  if (!Number.isFinite(seconds) || seconds <= 0) return '0초';
  const minutes = Math.floor(seconds / 60);
  const rest = Math.round(seconds % 60);
  return minutes > 0 ? `${minutes}분 ${rest}초` : `${rest}초`;
}

// '2026-09-14' → '9/14'
export function shortDate(date: string) {
  const [, month, day] = date.split('-');
  return `${Number(month)}/${Number(day)}`;
}

export function formatDateTime(ms: number) {
  return new Date(ms).toLocaleString('ko-KR', {
    timeZone: 'Asia/Seoul',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// 이전 기간 대비 변화율. 이전 값이 0이면 비교하지 않는다.
export function change(now: number, prev: number): number | null {
  if (!prev) return null;
  return (now - prev) / prev;
}

const regionNames = new Intl.DisplayNames(['ko'], { type: 'region' });

export function countryName(code: string) {
  if (!code || code === 'All' || code === 'Unknown') return code || '알 수 없음';
  try {
    return regionNames.of(code.toUpperCase()) ?? code;
  } catch {
    return code;
  }
}

// '3분 전', '2일 전'
export function relativeTime(iso: string | null, now = Date.now()) {
  if (!iso) return '—';
  const seconds = Math.max(0, (now - Date.parse(iso)) / 1000);
  if (seconds < 60) return '방금';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}분 전`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}시간 전`;
  return `${Math.floor(seconds / 86400)}일 전`;
}
