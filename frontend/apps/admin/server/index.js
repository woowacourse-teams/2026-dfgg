const { loadEnv, env, SetupError } = require('./env');
const { FUNNEL } = require('./config');
const { getUmamiSummary } = require('./umami');
const { getPosthogSummary } = require('./posthog');
const { getStoreSummary } = require('./store');
const { getStoreSummaryFromCsv } = require('./storeCsv');
const { mockUmami, mockStore, mockPlayers } = require('./mock');
const { getPlayers, getPlayersFromPosthog } = require('./players');

const DAY = 86_400_000;
const KST_OFFSET = 9 * 3600_000;
const ALLOWED_DAYS = [7, 30, 90];

// 분석 데이터 출처. 기본은 PostHog, ANALYTICS_SOURCE=umami 로 예전 Umami 를 볼 수 있다.
const analyticsSource = () =>
  env('ANALYTICS_SOURCE', 'posthog') === 'umami' ? 'umami' : 'posthog';

// 오늘(한국 시간) 포함 N일. 시작은 한국 시간 자정에 맞춘다.
function rangeFor(days) {
  const endAt = Date.now();
  const todayStart = Math.floor((endAt + KST_OFFSET) / DAY) * DAY - KST_OFFSET;
  return { startAt: todayStart - (days - 1) * DAY, endAt, timezone: 'Asia/Seoul', days };
}

async function settle(task) {
  try {
    return { ok: true, data: await task() };
  } catch (error) {
    return { ok: false, setup: error instanceof SetupError, error: error.message };
  }
}

function buildFunnel(analytics, store) {
  return FUNNEL.map((step) => {
    let value = null;
    if (step.source === 'analytics-web' && analytics.ok) value = analytics.data.web.visitors;
    if (step.source === 'analytics-event' && analytics.ok) {
      value = analytics.data.funnelVisitors[step.key] ?? 0;
    }
    if (step.source === 'store' && store.ok && !store.data.acquisitions.error) {
      value = store.data.acquisitions.total;
    }
    return {
      key: step.key,
      label: step.label,
      // 스토어 숫자는 우마미 방문자와 같은 사람인지 알 수 없다 — 화면에서 따로 표시한다.
      unit: step.source === 'store' ? 'acquisitions' : 'visitors',
      events: step.events ?? [],
      value,
    };
  });
}

// 스토어 데이터: Entra 키가 있으면 API, 없으면 data/ 의 CSV
const storeSummary = (range) =>
  env('MS_TENANT_ID') ? getStoreSummary(range) : getStoreSummaryFromCsv(range);

async function summary(days) {
  const range = rangeFor(days);
  const mock = env('ADMIN_MOCK') === '1';

  const source = analyticsSource();
  const getSummary = source === 'umami' ? getUmamiSummary : getPosthogSummary;
  const [analytics, store] = await Promise.all([
    settle(async () => (mock ? mockUmami(range) : getSummary(range))),
    settle(async () => (mock ? mockStore(range) : storeSummary(range))),
  ]);

  return {
    mock,
    range: { ...range, generatedAt: Date.now() },
    source,
    analytics,
    store,
    funnel: buildFunnel(analytics, store),
  };
}

/**
 * webpack dev server(express)에 관리자 API를 붙인다.
 * 이 코드는 로컬 Node 에서만 돌고, 빌드 결과물에는 포함되지 않는다.
 */
function registerAdminApi(app) {
  loadEnv();

  const daysOf = (req) => {
    const requested = Number(req.query.days);
    return ALLOWED_DAYS.includes(requested) ? requested : 30;
  };

  app.get('/admin-api/summary', async (req, res) => {
    try {
      res.json(await summary(daysOf(req)));
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  });

  app.get('/admin-api/players', async (req, res) => {
    const range = rangeFor(daysOf(req));
    const mock = env('ADMIN_MOCK') === '1';
    const source = analyticsSource();
    const load = source === 'umami' ? getPlayers : getPlayersFromPosthog;
    const result = await settle(async () => (mock ? mockPlayers(range) : load(range)));
    res.json({ mock, source, range: { ...range, generatedAt: Date.now() }, result });
  });
}

module.exports = { registerAdminApi };
