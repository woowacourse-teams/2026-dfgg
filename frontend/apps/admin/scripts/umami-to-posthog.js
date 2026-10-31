/**
 * 우마미에 쌓인 이벤트를 포스트호그로 옮기는 일회성 스크립트.
 *
 *   node scripts/umami-to-posthog.js dump     # 우마미 → scripts/.umami-dump.json
 *   node scripts/umami-to-posthog.js preview  # 변환 결과 몇 개와 이벤트별 개수만 출력
 *   node scripts/umami-to-posthog.js upload   # 덤프를 포스트호그로 전송
 *
 * .env 에 UMAMI_API_KEY, POSTHOG_PROJECT_KEY(phc_...) 가 필요하다.
 * 우마미 이벤트 id 를 포스트호그 uuid 로 그대로 써서, 다시 올려도 중복으로 쌓이지 않는다.
 */
const fs = require('fs');
const path = require('path');
const { loadEnv, env } = require('../server/env');

loadEnv();
const { umamiConfig, umamiGet } = require('../server/umami');

const DUMP_PATH = path.resolve(__dirname, '.umami-dump.json');
const PAGE_SIZE = 500;
const CONCURRENCY = 6;
const BATCH_SIZE = 500;
// 우마미 사이트를 만들기 전 시점이면 된다.
const START_AT = Date.UTC(2025, 0, 1);

async function listAll(config, pathName, range) {
  const rows = [];
  for (let page = 1; ; page += 1) {
    const body = await umamiGet(config, pathName, { ...range, page, pageSize: PAGE_SIZE });
    const data = body.data ?? [];
    rows.push(...data);
    process.stdout.write(`\r${pathName} ${rows.length}/${body.count ?? '?'}`);
    if (data.length < PAGE_SIZE || rows.length >= (body.count ?? 0)) break;
  }
  process.stdout.write('\n');
  return rows;
}

async function mapLimited(items, task) {
  const results = new Array(items.length);
  let next = 0;
  let done = 0;
  const worker = async () => {
    while (next < items.length) {
      const index = next++;
      results[index] = await task(items[index]);
      if (++done % 50 === 0) process.stdout.write(`\revent-data ${done}/${items.length}`);
    }
  };
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, items.length) }, worker));
  process.stdout.write(`\revent-data ${done}/${items.length}\n`);
  return results;
}

async function dump() {
  const config = umamiConfig();
  const range = { startAt: START_AT, endAt: Date.now() };

  const events = await listAll(config, '/events', range);
  const sessions = await listAll(config, '/sessions', range);

  // 커스텀 이벤트(eventType 2)만 속성이 있다.
  const custom = events.filter((event) => event.eventType === 2);
  const data = await mapLimited(custom, async (event) => {
    const rows = await umamiGet(config, `/event-data/${event.id}`, {});
    const props = {};
    for (const row of rows ?? []) {
      props[row.dataKey] = row.dataType === 2 ? Number(row.numberValue) : row.stringValue;
    }
    return [event.id, props];
  });

  fs.writeFileSync(
    DUMP_PATH,
    JSON.stringify({ range, events, sessions, eventData: Object.fromEntries(data) }),
  );
  console.log(`저장: ${DUMP_PATH}`);
  console.log('이벤트 필드:', Object.keys(events[0] ?? {}).join(', '));
  console.log('세션 필드:', Object.keys(sessions[0] ?? {}).join(', '));
}

const DEVICE_TYPES = { desktop: 'Desktop', laptop: 'Desktop', mobile: 'Mobile', tablet: 'Tablet' };

function toPosthog({ events, sessions, eventData }) {
  const sessionById = new Map(sessions.map((session) => [session.id, session]));

  return events.map((event) => {
    const session = sessionById.get(event.sessionId) ?? {};
    const hostname = event.hostname ?? session.hostname ?? 'dfgg.pro';
    const isPageview = event.eventType === 1;
    const query = event.urlQuery ? `?${event.urlQuery}` : '';
    const referrer = event.referrerDomain
      ? `https://${event.referrerDomain}${event.referrerPath ?? ''}`
      : undefined;

    const properties = {
      // 우마미에서 온 데이터임을 표시해 둔다.
      $lib: 'umami-import',
      umami_session_id: event.sessionId,
      // 이 스크립트를 돌리는 PC의 IP로 위치가 잡히지 않게 한다.
      $geoip_disable: true,
      $current_url: `https://${hostname}${event.urlPath ?? ''}${query}`,
      $host: hostname,
      $pathname: event.urlPath,
      $referrer: referrer,
      $referring_domain: event.referrerDomain || undefined,
      title: event.pageTitle || undefined,
      $browser: session.browser,
      $os: session.os,
      $device_type: DEVICE_TYPES[session.device] ?? session.device,
      $screen: session.screen,
      $browser_language: session.language,
      $geoip_country_code: session.country,
      $geoip_subdivision_1_code: session.region ?? session.subdivision1,
      $geoip_city_name: session.city,
      ...(isPageview ? {} : eventData[event.id]),
    };

    return {
      uuid: event.id,
      event: isPageview ? '$pageview' : event.eventName,
      // 데스크탑은 puuid 를 id 로 보냈다. 그 외에는 우마미 세션 단위로 묶는다.
      distinct_id: session.distinctId || `umami-${event.sessionId}`,
      timestamp: new Date(event.createdAt).toISOString(),
      properties: Object.fromEntries(
        Object.entries(properties).filter(([, value]) => value !== undefined && value !== null),
      ),
    };
  });
}

function readDump() {
  if (!fs.existsSync(DUMP_PATH)) throw new Error('먼저 dump 를 실행하세요.');
  return toPosthog(JSON.parse(fs.readFileSync(DUMP_PATH, 'utf8')));
}

function preview() {
  const events = readDump();
  const counts = {};
  for (const { event } of events) counts[event] = (counts[event] ?? 0) + 1;
  console.table(Object.entries(counts).sort((a, b) => b[1] - a[1]));
  console.log('총', events.length, '건, 사용자', new Set(events.map((e) => e.distinct_id)).size);
  const sample = (name) => events.find((e) => e.event === name);
  console.log(JSON.stringify([sample('$pageview'), sample('desktop-game-end')], null, 2));
}

async function upload() {
  const apiKey = env('POSTHOG_PROJECT_KEY');
  const host = env('POSTHOG_HOST', 'https://us.i.posthog.com').replace(/\/$/, '');
  if (!apiKey) throw new Error('POSTHOG_PROJECT_KEY 가 .env 에 없습니다.');

  const events = readDump();
  for (let i = 0; i < events.length; i += BATCH_SIZE) {
    const response = await fetch(`${host}/batch/`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        api_key: apiKey,
        // 과거 데이터를 한꺼번에 넣을 때 쓰는 별도 수집 경로를 탄다.
        historical_migration: true,
        batch: events.slice(i, i + BATCH_SIZE),
      }),
    });
    if (!response.ok) {
      throw new Error(`포스트호그 ${response.status} ${(await response.text()).slice(0, 200)}`);
    }
    process.stdout.write(`\rupload ${Math.min(i + BATCH_SIZE, events.length)}/${events.length}`);
  }
  process.stdout.write('\n완료\n');
}

const commands = { dump, preview, upload };
const command = commands[process.argv[2]];
if (!command) {
  console.log('사용법: node scripts/umami-to-posthog.js <dump|preview|upload>');
  process.exit(1);
}
Promise.resolve(command()).catch((error) => {
  console.error(error.message);
  process.exit(1);
});
