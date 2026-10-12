const { env, SetupError } = require('./env');

const API_BASE = 'https://manage.devcenter.microsoft.com/v1.0/my/analytics';
const RESOURCE = 'https://manage.devcenter.microsoft.com';

function storeConfig() {
  const tenantId = env('MS_TENANT_ID');
  const clientId = env('MS_CLIENT_ID');
  const clientSecret = env('MS_CLIENT_SECRET');
  const applicationId = env('MS_STORE_APP_ID', '9NXL98M7XC82');
  const missing = Object.entries({
    MS_TENANT_ID: tenantId,
    MS_CLIENT_ID: clientId,
    MS_CLIENT_SECRET: clientSecret,
  })
    .filter(([, value]) => !value)
    .map(([key]) => key);
  if (missing.length > 0) throw new SetupError(`${missing.join(', ')} 가 .env 에 없습니다.`);
  return { tenantId, clientId, clientSecret, applicationId };
}

// Entra 토큰은 60분짜리라 만료 1분 전까지 재사용한다.
let cachedToken = { value: '', expiresAt: 0 };

async function getToken(config) {
  if (cachedToken.value && Date.now() < cachedToken.expiresAt - 60_000) return cachedToken.value;

  const response = await fetch(
    `https://login.microsoftonline.com/${config.tenantId}/oauth2/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'client_credentials',
        client_id: config.clientId,
        client_secret: config.clientSecret,
        resource: RESOURCE,
      }),
      signal: AbortSignal.timeout(15000),
    },
  );
  const body = await response.json().catch(() => ({}));
  if (!response.ok || !body.access_token) {
    throw new Error(
      `Entra 토큰 발급 실패 ${response.status} ${body.error_description ?? body.error ?? ''}`.trim(),
    );
  }

  cachedToken = {
    value: body.access_token,
    expiresAt: Date.now() + Number(body.expires_in ?? 3600) * 1000,
  };
  return cachedToken.value;
}

// @nextLink 를 따라가며 모든 행을 모은다.
async function fetchAll(config, report, params) {
  const token = await getToken(config);
  const url = new URL(`${API_BASE}/${report}`);
  url.searchParams.set('applicationId', config.applicationId);
  for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);

  const rows = [];
  let next = url.toString();
  let freshness;
  while (next) {
    const response = await fetch(next, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
      signal: AbortSignal.timeout(20000),
    });
    if (!response.ok) {
      const text = (await response.text()).slice(0, 200);
      throw new Error(`스토어 ${report} ${response.status} ${text}`);
    }
    const body = await response.json();
    rows.push(...(body.Value ?? []));
    freshness = body.DataFreshnessTimestamp ?? freshness;
    next = body['@nextLink'] || '';
  }
  return { rows, freshness };
}

const ymd = (ms) => new Date(ms).toISOString().slice(0, 10);

function splitByPeriod(rows, valueKey, startDate) {
  const daily = new Map();
  let total = 0;
  let prevTotal = 0;
  for (const row of rows) {
    const date = String(row.date).slice(0, 10);
    const value = Number(row[valueKey] ?? 0);
    if (date >= startDate) {
      total += value;
      daily.set(date, (daily.get(date) ?? 0) + value);
    } else {
      prevTotal += value;
    }
  }
  return {
    total,
    prevTotal,
    daily: [...daily]
      .map(([date, count]) => ({ date, count }))
      .sort((a, b) => a.date.localeCompare(b.date)),
  };
}

async function settle(promise) {
  try {
    return await promise;
  } catch (error) {
    return { error: error.message };
  }
}

/**
 * 스토어 획득·설치. 직전 같은 길이 기간까지 한 번에 받아 증감을 낸다.
 * 스토어 데이터는 보통 1~3일 늦게 들어온다.
 */
async function getStoreSummary({ startAt, endAt }) {
  const config = storeConfig();
  const span = endAt - startAt;
  const startDate = ymd(startAt);
  const prevStartDate = ymd(startAt - span);
  const endDate = ymd(endAt);

  const daily = (report) =>
    fetchAll(config, report, {
      startDate: prevStartDate,
      endDate,
      aggregationLevel: 'day',
      groupby: 'date',
    });

  // 토큰 문제는 세 요청이 모두 같이 실패하므로 먼저 확인한다.
  await getToken(config);

  const [acquisitions, installs, markets] = await Promise.all([
    settle(
      daily('appacquisitions').then(({ rows, freshness }) => ({
        ...splitByPeriod(rows, 'acquisitionQuantity', startDate),
        freshness,
      })),
    ),
    settle(
      daily('installs').then(({ rows, freshness }) => ({
        ...splitByPeriod(rows, 'successfulInstallCount', startDate),
        freshness,
      })),
    ),
    settle(
      fetchAll(config, 'appacquisitions', {
        startDate,
        endDate,
        aggregationLevel: 'month',
        groupby: 'market',
      }).then(({ rows }) => {
        const byMarket = new Map();
        for (const row of rows) {
          byMarket.set(
            row.market,
            (byMarket.get(row.market) ?? 0) + Number(row.acquisitionQuantity ?? 0),
          );
        }
        return [...byMarket]
          .map(([x, y]) => ({ x, y }))
          .sort((a, b) => b.y - a.y)
          .slice(0, 10);
      }),
    ),
  ]);

  return { acquisitions, installs, markets };
}

module.exports = { getStoreSummary };
