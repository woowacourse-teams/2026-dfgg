/**
 * ADMIN_MOCK=1 일 때 쓰는 가짜 데이터. 키 없이 화면을 고칠 때 쓴다.
 * 실제 응답과 같은 모양이어야 한다 — umami.js / store.js 반환값을 바꾸면 여기도 맞춘다.
 */

// 시드 고정 난수 — 새로고침해도 같은 그림이 나온다.
function random(seed) {
  let state = seed;
  return () => {
    state = (state * 1664525 + 1013904223) % 4294967296;
    return state / 4294967296;
  };
}

const DAY = 86_400_000;

function mockUmami({ startAt, endAt }) {
  const rand = random(7);
  const daily = [];
  for (let t = startAt; t < endAt; t += DAY) {
    const date = new Date(t + 9 * 3600_000).toISOString().slice(0, 10);
    const sessions = Math.round(14 + rand() * 22);
    const launches = Math.round(2 + rand() * 7);
    daily.push({
      date,
      pageviews: Math.round(sessions * (1.6 + rand())),
      sessions,
      desktopEvents: launches * Math.round(18 + rand() * 20),
      launches,
    });
  }
  const sum = (key) => daily.reduce((total, row) => total + row[key], 0);
  const visitors = Math.round(sum('sessions') * 0.82);

  const events = [
    ['desktop-recommend-v3-fail', 1752],
    ['desktop-recommend-v3-success', 510],
    ['desktop-recommend-fail', 483],
    ['demo-stage-view', 306],
    ['desktop-recommend-mode-change', 298],
    ['recommend-item', 288],
    ['scroll-depth', 270],
    ['recommend-v3-success', 196],
    ['champion-select-random', 187],
    ['desktop-recommend-success', 155],
    ['desktop-app-launch', 150],
    ['recommend-success', 119],
    ['10-champion-recommend-top', 75],
    ['recommend-v3-error', 65],
    ['recommend-v3-fail', 62],
    ['desktop-lcu-connected', 53],
    ['desktop-lcu-failed', 50],
    ['download-click', 18],
    ['store-click-top', 9],
    ['desktop-install', 5],
  ].map(([name, count]) => ({
    name,
    count,
    platform: name.startsWith('desktop-') ? 'desktop' : 'web',
  }));

  return {
    web: {
      pageviews: sum('pageviews'),
      visitors,
      visits: sum('sessions'),
      bounces: Math.round(sum('sessions') * 0.4),
      totaltime: sum('sessions') * 58,
      prev: {
        pageviews: Math.round(sum('pageviews') * 0.55),
        visitors: Math.round(visitors * 0.52),
        visits: Math.round(sum('sessions') * 0.5),
        bounces: Math.round(sum('sessions') * 0.23),
        totaltime: sum('sessions') * 112,
      },
    },
    desktop: {
      events: sum('desktopEvents'),
      visitors: 159,
      visits: 288,
      prev: { events: Math.round(sum('desktopEvents') * 0.3), visitors: 56, visits: 80 },
    },
    daily,
    events,
    rates: {
      web: { success: 315, fail: 150 },
      desktop: { success: 665, fail: 2235 },
    },
    funnelVisitors: { 'app-cta': 41, 'app-launch': 95, 'lcu-connect': 31, recommend: 22 },
    top: {
      referrers: [
        { x: 'google.com', y: 112 },
        { x: 'naver.com', y: 64 },
        { x: 'fmkorea.com', y: 31 },
        { x: 'discord.com', y: 12 },
      ],
      paths: [
        { x: '/', y: 520 },
        { x: '/champion-select', y: 301 },
        { x: '/desktop-app', y: 128 },
        { x: '/feedback', y: 22 },
      ],
      countries: [
        { x: 'KR', y: 451 },
        { x: 'US', y: 18 },
        { x: 'JP', y: 9 },
      ],
    },
  };
}

function mockStore({ startAt, endAt }) {
  const rand = random(11);
  const daily = [];
  for (let t = startAt; t < endAt - 2 * DAY; t += DAY) {
    daily.push({
      date: new Date(t).toISOString().slice(0, 10),
      count: Math.round(rand() * 6),
    });
  }
  const total = daily.reduce((sum, row) => sum + row.count, 0);
  return {
    acquisitions: {
      total,
      prevTotal: Math.round(total * 0.6),
      daily,
      freshness: new Date(endAt - 2 * DAY).toISOString(),
    },
    installs: {
      total: Math.round(total * 0.85),
      prevTotal: Math.round(total * 0.5),
      daily: daily.map((row) => ({ ...row, count: Math.round(row.count * 0.85) })),
    },
    markets: [
      { x: 'KR', y: Math.round(total * 0.9) },
      { x: 'US', y: Math.round(total * 0.06) },
      { x: 'JP', y: Math.round(total * 0.04) },
    ],
  };
}

function mockPlayers({ startAt, endAt }) {
  // players.js 를 늦게 불러 순환 참조를 피한다.
  const { buildPlayers } = require('./players');
  const rand = random(23);
  const pick = (list) => list[Math.floor(rand() * list.length)];
  const champions = ['Briar', 'Jhin', 'Ahri', 'LeeSin', 'Thresh', 'Garen', 'Kaisa', 'Lux'];
  const positions = ['TOP', 'JUNGLE', 'MIDDLE', 'BOTTOM', 'UTILITY'];
  const results = ['win', 'win', 'lose', 'lose', 'unknown', 'app-quit'];

  const connects = [];
  const gameEnds = [];
  const dataById = new Map();
  for (let p = 1; p <= 6; p += 1) {
    const riotId = `테스트유저${p}#KR1`;
    const sessionId = `mock-session-${p}`;
    const base = {
      sessionId,
      country: 'KR',
      city: pick(['Seoul', 'Busan', 'Ansan-si']),
      os: 'Windows 10',
      hasData: 1,
    };
    const games = Math.floor(rand() * 7);
    const t0 = startAt + rand() * (endAt - startAt) * 0.6;
    const connectId = `mock-connect-${p}`;
    connects.push({
      ...base,
      id: connectId,
      eventName: 'desktop-connect',
      createdAt: new Date(t0).toISOString(),
    });
    dataById.set(connectId, { riotId, level: Math.round(30 + rand() * 500), version: '2.0.2' });
    for (let g = 0; g < games; g += 1) {
      const id = `mock-game-${p}-${g}`;
      const at = new Date(t0 + (g + 1) * (0.5 + rand()) * 3600_000 * 6);
      if (at.getTime() > endAt) break;
      const overlay = Math.round(600 + rand() * 1500);
      const purchases = Math.round(rand() * 7);
      gameEnds.push({ ...base, id, eventName: 'desktop-game-end', createdAt: at.toISOString() });
      dataById.set(id, {
        riotId,
        gameId: 8400000000 + Math.round(rand() * 9999999),
        queue: pick(['개인/2인 랭크 게임', '일반 게임', '칼바람 나락']),
        champion: pick(champions),
        position: pick(positions),
        result: pick(results),
        version: '2.0.2',
        overlaySec: overlay,
        overlayExpandedSec: Math.round(overlay * 0.9),
        overlayCollapsedSec: Math.round(overlay * 0.1),
        collapseToggles: Math.round(rand() * 3),
        recommendSuccess: Math.round(rand() * 8),
        recommendError: Math.round(rand() * 2),
        liveError: Math.round(rand() * 3),
        purchases,
        followed: Math.round(purchases * rand()),
        followedTop1: Math.round(purchases * rand() * 0.5),
      });
    }
  }
  return buildPlayers(connects, gameEnds, dataById);
}

module.exports = { mockUmami, mockStore, mockPlayers };
