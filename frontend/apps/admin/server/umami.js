const { env, SetupError } = require('./env');
const { DESKTOP_EVENT_PREFIX, FUNNEL, LAUNCH_EVENTS, RECOMMEND_PATTERN } = require('./config');

function umamiConfig() {
  const apiKey = env('UMAMI_API_KEY');
  const websiteId = env('UMAMI_WEBSITE_ID', '93de5e12-4372-4120-82c0-deab98b3f33a');
  // 우마미 클라우드 API. 셀프호스팅이면 https://<호스트>/api 로 바꾼다.
  const baseUrl = env('UMAMI_API_URL', 'https://api.umami.is/v1').replace(/\/$/, '');
  if (!apiKey) throw new SetupError('UMAMI_API_KEY 가 .env 에 없습니다.');
  return { apiKey, websiteId, baseUrl };
}

async function umamiGet(config, path, params) {
  const url = new URL(`${config.baseUrl}/websites/${config.websiteId}${path}`);
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) url.searchParams.set(key, String(value));
  }

  const response = await fetch(url, {
    headers: {
      Accept: 'application/json',
      // 클라우드 API 키는 x-umami-api-key, 셀프호스팅 토큰은 Bearer 로 받는다.
      'x-umami-api-key': config.apiKey,
      Authorization: `Bearer ${config.apiKey}`,
    },
    signal: AbortSignal.timeout(15000),
  });

  if (!response.ok) {
    const body = (await response.text()).slice(0, 200);
    throw new Error(`우마미 ${path} ${response.status} ${body}`);
  }
  return response.json();
}

const dateKey = (x) => String(x).slice(0, 10);

function pickTotals(stats) {
  // v2 응답({ pageviews: { value, prev } })과 v3 응답({ pageviews, comparison }) 둘 다 받는다.
  const read = (key) => {
    const value = stats[key];
    if (value && typeof value === 'object') return { now: value.value ?? 0, prev: value.prev ?? 0 };
    return { now: value ?? 0, prev: stats.comparison?.[key] ?? 0 };
  };
  const keys = ['pageviews', 'visitors', 'visits', 'bounces', 'totaltime'];
  const now = {};
  const prev = {};
  for (const key of keys) {
    const { now: n, prev: p } = read(key);
    now[key] = n;
    prev[key] = p;
  }
  return { ...now, prev };
}

function pickEventTotals(response) {
  const data = response.data ?? response;
  const pick = (source) => ({
    events: source?.events ?? 0,
    visitors: source?.visitors ?? 0,
    visits: source?.visits ?? 0,
  });
  return { ...pick(data), prev: pick(data.comparison) };
}

function platformOf(name) {
  return name.startsWith(DESKTOP_EVENT_PREFIX) ? 'desktop' : 'web';
}

function recommendRates(events) {
  const rates = {
    web: { success: 0, fail: 0 },
    desktop: { success: 0, fail: 0 },
  };
  for (const { name, count, platform } of events) {
    const match = RECOMMEND_PATTERN.exec(name);
    if (!match) continue;
    rates[platform][match[1] === 'success' ? 'success' : 'fail'] += count;
  }
  return rates;
}

/**
 * 기간 하나에 대한 우마미 요약.
 * 데스크탑 앱은 페이지뷰 없이 이벤트만 보내므로, 데스크탑 지표는 "desktop-" 이벤트로 센다.
 */
async function getUmamiSummary({ startAt, endAt, timezone }) {
  const config = umamiConfig();
  const range = { startAt, endAt };
  const get = (path, params = {}) => umamiGet(config, path, { ...range, ...params });

  const funnelEventSteps = FUNNEL.filter((step) => step.source === 'analytics-event');

  const [
    stats,
    desktopStats,
    pageviews,
    series,
    eventMetrics,
    referrers,
    paths,
    countries,
    ...funnelStats
  ] = await Promise.all([
    get('/stats'),
    get('/events/stats', { event: `c.${DESKTOP_EVENT_PREFIX}` }),
    get('/pageviews', { unit: 'day', timezone }),
    get('/events/series', { unit: 'day', timezone }),
    get('/metrics', { type: 'event', limit: 500 }),
    get('/metrics', { type: 'referrer', limit: 10 }),
    get('/metrics', { type: 'path', limit: 10 }),
    get('/metrics', { type: 'country', limit: 10 }),
    ...funnelEventSteps.map((step) => get('/events/stats', { event: step.events.join(',') })),
  ]);

  const days = new Map();
  const day = (date) => {
    if (!days.has(date)) {
      days.set(date, { date, pageviews: 0, sessions: 0, desktopEvents: 0, launches: 0 });
    }
    return days.get(date);
  };
  for (const { x, y } of pageviews.pageviews ?? []) day(dateKey(x)).pageviews = y;
  for (const { x, y } of pageviews.sessions ?? []) day(dateKey(x)).sessions = y;
  for (const { x, t, y } of series ?? []) {
    if (platformOf(x) !== 'desktop') continue;
    const row = day(dateKey(t));
    row.desktopEvents += y;
    if (LAUNCH_EVENTS.includes(x)) row.launches += y;
  }

  const events = (eventMetrics ?? []).map(({ x, y }) => ({
    name: x,
    count: y,
    platform: platformOf(x),
  }));

  const funnelVisitors = {};
  funnelEventSteps.forEach((step, index) => {
    funnelVisitors[step.key] = pickEventTotals(funnelStats[index]).visitors;
  });

  return {
    web: pickTotals(stats),
    desktop: pickEventTotals(desktopStats),
    daily: [...days.values()].sort((a, b) => a.date.localeCompare(b.date)),
    events,
    rates: recommendRates(events),
    funnelVisitors,
    top: { referrers, paths, countries },
  };
}

module.exports = { getUmamiSummary, umamiConfig, umamiGet };
