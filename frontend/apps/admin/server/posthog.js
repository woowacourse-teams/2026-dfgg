const { env, SetupError } = require('./env');
const { FUNNEL, LAUNCH_EVENTS, RECOMMEND_PATTERN } = require('./config');

/**
 * PostHog HogQL 로 대시보드 숫자를 뽑는다.
 * 웹과 데스크탑 앱이 한 프로젝트에 같이 쌓이므로, desktop- 이벤트이거나
 * platform=desktop 인 이벤트를 데스크탑으로 본다.
 */

const DESKTOP = "(event LIKE 'desktop-%' OR ifNull(properties.platform, '') = 'desktop')";
const WEB = `NOT ${DESKTOP}`;
const RANGE = 'timestamp >= toDateTime({start}) AND timestamp < toDateTime({end})';
// posthog-js 는 $session_id 를, Umami 에서 옮긴 이벤트는 umami_visit_id 를 갖는다.
const VISIT = 'coalesce(properties.$session_id, properties.umami_visit_id)';
const TZ = 'Asia/Seoul';

function posthogConfig() {
  const apiKey = env('POSTHOG_PERSONAL_API_KEY');
  const projectId = env('POSTHOG_PROJECT_ID');
  const host = env('POSTHOG_HOST', 'https://us.posthog.com').replace(/\/$/, '');
  if (!apiKey || !projectId) {
    throw new SetupError('POSTHOG_PERSONAL_API_KEY, POSTHOG_PROJECT_ID 가 .env 에 없습니다.');
  }
  return { apiKey, projectId, host };
}

async function hogql(config, query, values = {}) {
  const response = await fetch(`${config.host}/api/projects/${config.projectId}/query/`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${config.apiKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ query: { kind: 'HogQLQuery', query, values } }),
    signal: AbortSignal.timeout(30000),
  });
  if (!response.ok) {
    const body = (await response.text()).slice(0, 300);
    throw new Error(`PostHog 쿼리 ${response.status} ${body}`);
  }
  return (await response.json()).results ?? [];
}

// ms → HogQL toDateTime 이 받는 UTC 문자열
const utc = (ms) => new Date(ms).toISOString().slice(0, 19).replace('T', ' ');

async function webTotals(config, values) {
  const [[pageviews, visitors, visits]] = await hogql(
    config,
    `SELECT count(), uniq(distinct_id), uniq(${VISIT})
     FROM events WHERE event = '$pageview' AND ${WEB} AND ${RANGE}`,
    values,
  );
  // 방문(세션)별 페이지뷰 수와 머문 시간
  const [[bounces, totaltime]] = await hogql(
    config,
    `SELECT countIf(pv = 1), sum(dur) FROM (
       SELECT ${VISIT} AS v, countIf(event = '$pageview') AS pv,
              dateDiff('second', min(timestamp), max(timestamp)) AS dur
       FROM events WHERE ${WEB} AND ${RANGE} GROUP BY v HAVING pv > 0)`,
    values,
  );
  return { pageviews, visitors, visits, bounces: bounces ?? 0, totaltime: totaltime ?? 0 };
}

async function desktopTotals(config, values) {
  const [[events, visitors, visits]] = await hogql(
    config,
    `SELECT count(), uniq(distinct_id), uniq(${VISIT}) FROM events WHERE ${DESKTOP} AND ${RANGE}`,
    values,
  );
  return { events, visitors, visits };
}

function recommendRates(events) {
  const rates = { web: { success: 0, fail: 0 }, desktop: { success: 0, fail: 0 } };
  for (const { name, count, platform } of events) {
    const match = RECOMMEND_PATTERN.exec(name);
    if (match) rates[platform][match[1] === 'success' ? 'success' : 'fail'] += count;
  }
  return rates;
}

const top = (config, values, column) =>
  hogql(
    config,
    `SELECT ${column} AS x, count() AS y FROM events
     WHERE event = '$pageview' AND ${WEB} AND ${RANGE} AND x IS NOT NULL
     GROUP BY x ORDER BY y DESC LIMIT 10`,
    values,
  ).then((rows) => rows.map(([x, y]) => ({ x, y })));

/** umami.js 의 getUmamiSummary 와 같은 모양으로 돌려준다. */
async function getPosthogSummary({ startAt, endAt }) {
  const config = posthogConfig();
  const span = endAt - startAt;
  const now = { start: utc(startAt), end: utc(endAt) };
  const prev = { start: utc(startAt - span), end: utc(startAt) };
  const eventSteps = FUNNEL.filter((step) => step.source === 'analytics-event');

  const [
    web,
    webPrev,
    desktop,
    desktopPrev,
    daily,
    eventRows,
    funnelRow,
    referrers,
    paths,
    countries,
  ] = await Promise.all([
    webTotals(config, now),
    webTotals(config, prev),
    desktopTotals(config, now),
    desktopTotals(config, prev),
    hogql(
      config,
      `SELECT toString(toDate(toTimeZone(timestamp, '${TZ}'))) AS d,
                countIf(event = '$pageview' AND ${WEB}),
                uniqIf(${VISIT}, event = '$pageview' AND ${WEB}),
                countIf(${DESKTOP}),
                countIf(event IN {launch})
         FROM events WHERE ${RANGE} GROUP BY d ORDER BY d LIMIT 1000`,
      { ...now, launch: LAUNCH_EVENTS },
    ),
    hogql(
      config,
      `SELECT event, count(), ${DESKTOP} FROM events
         WHERE ${RANGE} AND event NOT LIKE '$%'
         GROUP BY event, ${DESKTOP} ORDER BY 2 DESC LIMIT 1000`,
      now,
    ),
    hogql(
      config,
      `SELECT ${eventSteps.map((_, i) => `uniqIf(distinct_id, event IN {s${i}})`).join(', ')}
         FROM events WHERE ${RANGE}`,
      { ...now, ...Object.fromEntries(eventSteps.map((step, i) => [`s${i}`, step.events])) },
    ),
    top(config, now, 'properties.$referring_domain'),
    top(config, now, 'properties.$pathname'),
    top(config, now, 'properties.$geoip_country_code'),
  ]);

  const events = eventRows.map(([name, count, isDesktop]) => ({
    name,
    count,
    platform: isDesktop ? 'desktop' : 'web',
  }));
  const funnelVisitors = Object.fromEntries(
    eventSteps.map((step, i) => [step.key, funnelRow[0]?.[i] ?? 0]),
  );

  return {
    web: { ...web, prev: webPrev },
    desktop: { ...desktop, prev: desktopPrev },
    daily: daily.map(([date, pageviews, sessions, desktopEvents, launches]) => ({
      date,
      pageviews,
      sessions,
      desktopEvents,
      launches,
    })),
    events,
    rates: recommendRates(events),
    funnelVisitors,
    top: { referrers, paths, countries },
  };
}

/**
 * 앱 사용자 페이지용: desktop-connect / desktop-game-end 이벤트를 속성째로 받는다.
 * players.js 의 buildPlayers 가 받는 Umami 이벤트 모양으로 바꿔 준다.
 */
async function getPosthogPlayerEvents({ startAt, endAt }) {
  const config = posthogConfig();
  const rows = await hogql(
    config,
    `SELECT uuid, event, timestamp, distinct_id, properties FROM events
     WHERE event IN ('desktop-connect', 'desktop-game-end') AND ${RANGE}
     ORDER BY timestamp LIMIT 50000`,
    { start: utc(startAt), end: utc(endAt) },
  );

  const connects = [];
  const gameEnds = [];
  const dataById = new Map();
  for (const [uuid, event, timestamp, distinctId, rawProperties] of rows) {
    const properties =
      typeof rawProperties === 'string' ? JSON.parse(rawProperties) : (rawProperties ?? {});
    const row = {
      id: uuid,
      eventName: event,
      createdAt: timestamp,
      // 데스크탑 앱은 소환사(PUUID) 단위 id 로 보내므로 distinct_id 가 사용자 단위다.
      sessionId: distinctId,
      hasData: 1,
      country: properties.$geoip_country_code,
      city: properties.$geoip_city_name,
      os: properties.$os_version ?? properties.$os,
    };
    dataById.set(uuid, properties);
    (event === 'desktop-connect' ? connects : gameEnds).push(row);
  }

  const legacy = await getLegacyPlayerEvents(config, { start: utc(startAt), end: utc(endAt) });
  connects.push(...legacy.connects);
  gameEnds.push(...legacy.gameEnds);
  for (const [id, data] of legacy.dataById) dataById.set(id, data);
  return { connects, gameEnds, dataById };
}

/**
 * 구버전 앱(desktop-connect / desktop-game-end 를 보내기 전)을 쓴 사용자.
 * Riot ID·챔피언·승패는 없어서, 챔피언 선택·게임 감지(desktop-session-detected)부터
 * 다음 감지 전까지 들어온 추천 이벤트를 한 판으로 추정한다.
 */
const LEGACY_EVENTS = [
  'desktop-lcu-connected',
  'desktop-session-detected',
  'desktop-update-check-result',
  'desktop-recommend-success',
  'desktop-recommend-fail',
  'desktop-recommend-v3-success',
  'desktop-recommend-v3-fail',
];
// 이 시간 안에 다시 감지되면(챔피언 선택 → 게임 시작) 같은 판으로 본다.
const SAME_GAME_MS = 15 * 60_000;
// 감지 없이 추천 요청만 계속 오는 경우(구버전이 실패를 재시도)가 있어 한 판은 길어야 90분으로 본다.
const MAX_GAME_MS = 90 * 60_000;

async function getLegacyPlayerEvents(config, values) {
  const rows = await hogql(
    config,
    `SELECT uuid, event, timestamp, distinct_id, properties.$geoip_city_name,
            properties.$geoip_country_code, coalesce(properties.$os_version, properties.$os),
            properties.version
     FROM events
     WHERE event IN (${LEGACY_EVENTS.map((name) => `'${name}'`).join(', ')}) AND ${RANGE}
       AND distinct_id NOT IN (
         SELECT distinct_id FROM events WHERE event IN ('desktop-connect', 'desktop-game-end'))
     ORDER BY distinct_id, timestamp LIMIT 100000`,
    values,
  );

  const connects = [];
  const gameEnds = [];
  const dataById = new Map();
  const versions = new Map();
  let game = null;

  const closeGame = () => {
    if (game && game.recommends > 0) {
      gameEnds.push({ ...game.row, createdAt: game.lastAt });
      dataById.set(game.row.id, {
        result: 'legacy',
        recommendSuccess: game.success,
        recommendError: game.fail,
        version: versions.get(game.row.sessionId),
      });
    }
    game = null;
  };

  for (const [uuid, event, timestamp, distinctId, city, country, os, version] of rows) {
    if (game && game.row.sessionId !== distinctId) closeGame();
    const row = {
      id: uuid,
      eventName: event,
      createdAt: timestamp,
      sessionId: distinctId,
      hasData: 1,
      city,
      country,
      os,
    };
    const at = Date.parse(timestamp);

    if (event === 'desktop-update-check-result') {
      if (version) versions.set(distinctId, version);
    } else if (event === 'desktop-lcu-connected') {
      connects.push(row);
      dataById.set(uuid, { version: versions.get(distinctId) });
    } else if (event === 'desktop-session-detected') {
      if (game && at - game.detectedAt < SAME_GAME_MS) {
        game.detectedAt = at;
      } else {
        closeGame();
        game = { row, detectedAt: at, lastAt: timestamp, recommends: 0, success: 0, fail: 0 };
      }
    } else if (game && at - Date.parse(game.row.createdAt) > MAX_GAME_MS) {
      closeGame();
    } else if (game) {
      // 인게임 추천(v3)만 성공·실패로 센다. 구 추천(챔피언 선택 화면)은 판 판정에만 쓴다.
      game.recommends += 1;
      game.lastAt = timestamp;
      if (event === 'desktop-recommend-v3-success') game.success += 1;
      if (event === 'desktop-recommend-v3-fail') game.fail += 1;
    }
  }
  closeGame();
  return { connects, gameEnds, dataById };
}

module.exports = { getPosthogSummary, getPosthogPlayerEvents };
