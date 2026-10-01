const fs = require('fs');
const path = require('path');

const { SetupError } = require('./env');

/**
 * Partner Center 에서 내려받은 CSV 로 스토어 지표를 만든다.
 * Insights → 다운로드 허브 → 새 보고서 만들기(취득/설치, 일별) 로 받은 파일을
 * apps/admin/data/ 에 넣으면 된다. 파일이 여러 개면 합치고, 똑같은 행은 한 번만 센다.
 *
 * 보고서 종류·언어마다 열 이름이 달라서 머리글 키워드로 열을 찾는다.
 */
const DATA_DIR = path.resolve(__dirname, '../data');

const COLUMNS = {
  date: /^(date|날짜|일자|report ?date|acquisition ?date|install ?date)$|date$|날짜$/i,
  pageviews: /page ?views?|페이지 ?(조회|보기)/i,
  acquisitions: /acquisition ?(quantity|count)|^acquisitions?$|^(취득|획득)( ?수| ?수량)?$/i,
  installs: /install(s| ?count| ?quantity)?$|^설치( ?수)?$|successful ?install/i,
  market: /^(market|시장|country|국가|country ?\/ ?region|국가 ?\/ ?지역|region)$/i,
};

// 따옴표·쉼표·탭을 처리하는 작은 CSV 파서 (의존성 없이)
function parseCsv(text) {
  const body = text.replace(/^\uFEFF/, '');
  const firstLine = body.slice(0, body.indexOf('\n') >>> 0);
  const delimiter = firstLine.split('\t').length > firstLine.split(',').length ? '\t' : ',';

  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < body.length; i += 1) {
    const char = body[i];
    if (quoted) {
      if (char === '"' && body[i + 1] === '"') {
        field += '"';
        i += 1;
      } else if (char === '"') {
        quoted = false;
      } else {
        field += char;
      }
    } else if (char === '"') {
      quoted = true;
    } else if (char === delimiter) {
      row.push(field);
      field = '';
    } else if (char === '\n' || char === '\r') {
      if (char === '\r' && body[i + 1] === '\n') i += 1;
      row.push(field);
      if (row.some((cell) => cell !== '')) rows.push(row);
      row = [];
      field = '';
    } else {
      field += char;
    }
  }
  row.push(field);
  if (row.some((cell) => cell !== '')) rows.push(row);
  return rows;
}

function findColumns(header) {
  const found = {};
  for (const [key, pattern] of Object.entries(COLUMNS)) {
    const index = header.findIndex((name) => pattern.test(name.trim()));
    if (index !== -1) found[key] = index;
  }
  return found;
}

// '2026-09-30', '2026/09/30', '2026. 9. 30.', '9/30/2026', '2026-09-30T00:00:00' → '2026-09-30'
function toDate(value) {
  const text = value.trim();
  let match = /^(\d{4})\s*[-/.]\s*(\d{1,2})\s*[-/.]\s*(\d{1,2})/.exec(text);
  if (match) return `${match[1]}-${match[2].padStart(2, '0')}-${match[3].padStart(2, '0')}`;
  match = /^(\d{1,2})\/(\d{1,2})\/(\d{4})/.exec(text);
  if (match) return `${match[3]}-${match[1].padStart(2, '0')}-${match[2].padStart(2, '0')}`;
  return null;
}

const toNumber = (value) => Number(String(value ?? '').replace(/[,\s]/g, '')) || 0;

// Partner Center 취득 화면의 차트별 다운로드는 파일 이름으로 종류를 구분한다.
// (예: Apps-and-Games-Installs.csv → "날짜","All" 주별 값)
const CHART_FILES = [
  { pattern: /Page-views/i, kind: 'series', metric: 'pageviews' },
  {
    pattern: /Apps-and-Games-Installs(\s*\(\d+\))?\.(csv|tsv)$/i,
    kind: 'series',
    metric: 'installs',
  },
  { pattern: /Acquisitions-Geographical-spread/i, kind: 'markets' },
  { pattern: /Acquisition-funnel/i, kind: 'funnel' },
];

// 퍼널 CSV 의 범주 이름 → 키
const FUNNEL_KEYS = [
  { key: 'pageviews', pattern: /페이지 ?조회|page ?views?/i },
  { key: 'attempts', pattern: /설치 ?시도|install ?attempt/i },
  { key: 'installs', pattern: /성공한 ?설치|successful ?install/i },
  { key: 'firstLaunch', pattern: /처음 ?출시|첫 ?실행|first ?launch/i },
];

/**
 * data/ 의 파일을 모두 읽는다.
 * - rows: { date, market, pageviews, acquisitions, installs } (일별 또는 주별)
 * - markets: 국가별 설치 (Partner Center 지리적 분포, 내려받은 기간 전체)
 * - funnel: 스토어 퍼널 (내려받은 기간 전체)
 */
function readData() {
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
  const files = fs.readdirSync(DATA_DIR).filter((name) => /\.(csv|tsv)$/i.test(name));
  if (files.length === 0) {
    throw new SetupError(
      'apps/admin/data/ 에 Partner Center 에서 내려받은 CSV 가 없습니다. (Insights → 취득 → 각 차트의 다운로드)',
    );
  }

  const seen = new Set();
  const rows = [];
  const used = [];
  const skipped = [];
  let markets = null;
  let funnel = null;

  for (const file of files) {
    const [header = [], ...lines] = parseCsv(fs.readFileSync(path.join(DATA_DIR, file), 'utf8'));
    const chart = CHART_FILES.find(({ pattern }) => pattern.test(file));

    if (chart?.kind === 'markets') {
      markets = lines
        .map((line) => ({ x: line[0]?.trim(), y: toNumber(line[1]) }))
        .filter((point) => point.x && point.y > 0);
      used.push(file);
      continue;
    }
    if (chart?.kind === 'funnel') {
      funnel = {};
      for (const [label, count] of lines) {
        const match = FUNNEL_KEYS.find(({ pattern }) => pattern.test(label ?? ''));
        if (match) funnel[match.key] = toNumber(count);
      }
      used.push(file);
      continue;
    }

    const columns = findColumns(header);
    // 차트 다운로드는 값 열 이름이 "All" 이라 파일 이름으로 지표를 정한다.
    if (chart?.kind === 'series' && 'date' in columns)
      columns[chart.metric] = columns.date === 0 ? 1 : 0;
    const metrics = ['pageviews', 'acquisitions', 'installs'].filter((key) => key in columns);
    if (!('date' in columns) || metrics.length === 0) {
      skipped.push(file);
      continue;
    }
    used.push(file);
    for (const line of lines) {
      const key = `${metrics.join()}\u0001${line.join('\u0001')}`;
      if (seen.has(key)) continue;
      seen.add(key);
      const date = toDate(line[columns.date] ?? '');
      if (!date) continue;
      rows.push({
        date,
        market: 'market' in columns ? line[columns.market]?.trim() || null : null,
        pageviews: 'pageviews' in columns ? toNumber(line[columns.pageviews]) : null,
        acquisitions: 'acquisitions' in columns ? toNumber(line[columns.acquisitions]) : null,
        installs: 'installs' in columns ? toNumber(line[columns.installs]) : null,
      });
    }
  }
  if (rows.length === 0 && !markets && !funnel) {
    throw new Error(`읽을 수 있는 파일이 없습니다. 건너뛴 파일: ${skipped.join(', ')}`);
  }
  return { rows, markets, funnel, used, skipped };
}

const DAY = 86_400_000;
const kstDate = (ms) => new Date(ms + 9 * 3600_000).toISOString().slice(0, 10);
const dayNumber = (date) => Math.floor(Date.parse(`${date}T00:00:00Z`) / DAY);

// 날짜 간격이 7일이면 주별 데이터(날짜 = 그 주의 월요일)
function bucketDays(dates) {
  const sorted = [...new Set(dates)].sort();
  for (let i = 1; i < sorted.length; i += 1) {
    if (dayNumber(sorted[i]) - dayNumber(sorted[i - 1]) === 7) return 7;
  }
  return 1;
}

// 구간 [from, to] 에 겹치는 비율만큼 센다. 주별 데이터를 7일·30일 같은 기간에 맞추기 위한 근사.
function overlap(date, span, from, to) {
  const start = dayNumber(date);
  const end = start + span - 1;
  const days = Math.min(end, dayNumber(to)) - Math.max(start, dayNumber(from)) + 1;
  return days > 0 ? days / span : 0;
}

function series(rows, metric, startDate, prevStartDate, endDate) {
  const relevant = rows.filter((row) => row[metric] !== null);
  if (relevant.length === 0) return { error: `${metric} 데이터가 없습니다.` };

  const span = bucketDays(relevant.map((row) => row.date));
  const prevEndDate = kstDate(Date.parse(`${startDate}T00:00:00+09:00`) - DAY);
  const buckets = new Map();
  let total = 0;
  let prevTotal = 0;
  for (const row of relevant) {
    buckets.set(row.date, (buckets.get(row.date) ?? 0) + row[metric]);
    total += row[metric] * overlap(row.date, span, startDate, endDate);
    prevTotal += row[metric] * overlap(row.date, span, prevStartDate, prevEndDate);
  }
  const dates = [...buckets.keys()].sort();
  const last = dates[dates.length - 1];
  return {
    total: Math.round(total),
    prevTotal: Math.round(prevTotal),
    granularity: span === 7 ? 'week' : 'day',
    daily: dates
      .filter((date) => overlap(date, span, startDate, endDate) > 0)
      .map((date) => ({ date, count: buckets.get(date) })),
    // 데이터가 들어 있는 마지막 날 — 그 뒤는 차트에서 빈칸
    freshness: `${kstDate(Date.parse(`${last}T00:00:00+09:00`) + (span - 1) * DAY)}T23:59:59+09:00`,
  };
}

/** store.js 의 getStoreSummary 와 같은 모양에 pageviews·funnel 을 더해 돌려준다. */
function getStoreSummaryFromCsv({ startAt, endAt }) {
  const { rows, markets, funnel, used, skipped } = readData();
  const startDate = kstDate(startAt);
  const endDate = kstDate(endAt);
  const prevStartDate = kstDate(startAt - (endAt - startAt));

  // 취득 열이 없으면(설치 보고서만 있으면) 설치 수를 획득으로 쓴다.
  const hasAcquisitions = rows.some((row) => row.acquisitions !== null);
  const acquisitionMetric = hasAcquisitions ? 'acquisitions' : 'installs';

  // 직접 만든 CSV 에 시장 열이 있으면 그걸로, 없으면 지리적 분포 파일로
  const byMarket = new Map();
  for (const row of rows) {
    const value = row[acquisitionMetric] ?? 0;
    if (!row.market || value <= 0 || row.date < startDate || row.date > endDate) continue;
    byMarket.set(row.market, (byMarket.get(row.market) ?? 0) + value);
  }
  const marketList = byMarket.size ? [...byMarket].map(([x, y]) => ({ x, y })) : markets;

  return {
    acquisitions: series(rows, acquisitionMetric, startDate, prevStartDate, endDate),
    installs: series(rows, 'installs', startDate, prevStartDate, endDate),
    pageviews: series(rows, 'pageviews', startDate, prevStartDate, endDate),
    markets: marketList?.length
      ? marketList.sort((a, b) => b.y - a.y).slice(0, 10)
      : { error: '국가별 데이터가 없습니다 (Acquisitions-Geographical-spread.csv).' },
    // 지리적 분포·퍼널 파일은 날짜가 없어 기간 선택과 상관없이 내려받은 기간 전체다.
    marketsWholePeriod: !byMarket.size && Boolean(markets?.length),
    funnel: funnel && Object.keys(funnel).length ? funnel : null,
    files: { used, skipped },
  };
}

module.exports = { getStoreSummaryFromCsv, parseCsv, findColumns };
