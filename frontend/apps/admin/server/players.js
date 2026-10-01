const { umamiConfig, umamiGet } = require('./umami');
const { getPosthogPlayerEvents } = require('./posthog');

/**
 * 데스크탑 앱 사용자별 정리.
 * desktop-connect(앱이 롤 클라이언트에 붙은 순간)와 desktop-game-end(한 판 끝날 때)에
 * riotId 가 실려 있어서, 이 두 이벤트를 Riot ID 기준으로 묶는다.
 */

const CONNECT_EVENT = 'desktop-connect';
const GAME_EVENT = 'desktop-game-end';
const PAGE_SIZE = 200;
const CONCURRENCY = 6;

// 이벤트 데이터는 한 번 쌓이면 바뀌지 않으므로 dev server 가 떠 있는 동안 캐시한다.
const eventDataCache = new Map();

async function listEvents(config, name, range) {
  const rows = [];
  for (let page = 1; ; page += 1) {
    const body = await umamiGet(config, '/events', {
      ...range,
      search: name,
      page,
      pageSize: PAGE_SIZE,
    });
    rows.push(...(body.data ?? []));
    if ((body.data ?? []).length < PAGE_SIZE || rows.length >= (body.count ?? 0)) break;
  }
  // search 는 부분 일치라 이름이 정확히 같은 것만 남긴다.
  return rows.filter((row) => row.eventName === name);
}

async function eventData(config, eventId) {
  if (!eventDataCache.has(eventId)) {
    const rows = await umamiGet(config, `/event-data/${eventId}`, {});
    const data = {};
    for (const row of rows ?? []) {
      data[row.dataKey] = row.dataType === 2 ? Number(row.numberValue) : row.stringValue;
    }
    eventDataCache.set(eventId, data);
  }
  return eventDataCache.get(eventId);
}

// 동시에 CONCURRENCY 개까지만 요청한다.
async function mapLimited(items, task) {
  const results = new Array(items.length);
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      const index = next++;
      results[index] = await task(items[index]);
    }
  };
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, items.length) }, worker));
  return results;
}

const num = (value) => (typeof value === 'number' && Number.isFinite(value) ? value : 0);

function toGame(event, data) {
  return {
    id: event.id,
    at: event.createdAt,
    gameId: data.gameId ? String(Math.round(num(data.gameId))) : null,
    queue: data.queue ?? null,
    champion: data.champion ?? null,
    position: data.position ?? null,
    result: data.result ?? 'unknown',
    version: data.version ?? null,
    overlaySec: num(data.overlaySec),
    overlayExpandedSec: num(data.overlayExpandedSec),
    overlayCollapsedSec: num(data.overlayCollapsedSec),
    collapseToggles: num(data.collapseToggles),
    recommendSuccess: num(data.recommendSuccess),
    recommendError: num(data.recommendError),
    liveError: num(data.liveError),
    purchases: num(data.purchases),
    followed: num(data.followed),
    followedTop1: num(data.followedTop1),
  };
}

function emptyPlayer(key, riotId) {
  return {
    key,
    riotId,
    level: null,
    version: null,
    firstAt: null,
    lastAt: null,
    connects: 0,
    location: null,
    os: null,
    games: [],
  };
}

function touch(player, event) {
  if (!player.firstAt || event.createdAt < player.firstAt) player.firstAt = event.createdAt;
  if (!player.lastAt || event.createdAt >= player.lastAt) {
    player.lastAt = event.createdAt;
    player.location = [event.city, event.country].filter(Boolean).join(', ') || player.location;
    player.os = event.os || player.os;
  }
}

function summarize(player) {
  const games = player.games.sort((a, b) => b.at.localeCompare(a.at));
  const sum = (key) => games.reduce((total, game) => total + game[key], 0);
  const count = (predicate) => games.filter(predicate).length;
  const champions = new Map();
  for (const game of games) {
    if (game.champion) champions.set(game.champion, (champions.get(game.champion) ?? 0) + 1);
  }

  return {
    ...player,
    games,
    stats: {
      games: games.length,
      wins: count((game) => game.result === 'win'),
      losses: count((game) => game.result === 'lose'),
      overlaySec: sum('overlaySec'),
      purchases: sum('purchases'),
      followed: sum('followed'),
      followedTop1: sum('followedTop1'),
      recommendSuccess: sum('recommendSuccess'),
      recommendError: sum('recommendError'),
      liveError: sum('liveError'),
      topChampions: [...champions]
        .sort((a, b) => b[1] - a[1])
        .slice(0, 3)
        .map(([name, n]) => ({ name, games: n })),
    },
  };
}

async function getPlayers({ startAt, endAt }) {
  const config = umamiConfig();
  const range = { startAt, endAt };

  const [connects, gameEnds] = await Promise.all([
    listEvents(config, CONNECT_EVENT, range),
    listEvents(config, GAME_EVENT, range),
  ]);

  const withData = [...connects, ...gameEnds].filter((event) => event.hasData);
  const data = await mapLimited(withData, (event) => eventData(config, event.id));
  const dataById = new Map(withData.map((event, index) => [event.id, data[index]]));

  return buildPlayers(connects, gameEnds, dataById);
}

// events: 우마미 /events 행, dataById: 이벤트 id → { riotId, champion, ... }
function buildPlayers(connects, gameEnds, dataById) {
  // Riot ID 가 없으면(구버전 등) 우마미 세션 단위로 따로 둔다.
  // 데스크탑 앱은 PUUID 로 세션을 묶으므로, 같은 세션에서 한 번이라도 Riot ID 가 보이면
  // Riot ID 없이 온 이벤트도 그 사용자로 본다.
  const riotIdBySession = new Map();
  for (const event of [...connects, ...gameEnds]) {
    const riotId = dataById.get(event.id)?.riotId;
    if (riotId) riotIdBySession.set(event.sessionId, riotId);
  }

  const players = new Map();
  const playerFor = (event, ownRiotId) => {
    const riotId = ownRiotId ?? riotIdBySession.get(event.sessionId);
    const key = riotId ? `riot:${riotId}` : `session:${event.sessionId}`;
    if (!players.has(key)) players.set(key, emptyPlayer(key, riotId ?? null));
    return players.get(key);
  };

  for (const event of connects) {
    const values = dataById.get(event.id) ?? {};
    const player = playerFor(event, values.riotId);
    player.connects += 1;
    if (!player.lastAt || event.createdAt >= player.lastAt) {
      if (values.level) player.level = num(values.level);
      if (values.version) player.version = values.version;
    }
    touch(player, event);
  }

  for (const event of gameEnds) {
    const values = dataById.get(event.id) ?? {};
    const player = playerFor(event, values.riotId);
    player.games.push(toGame(event, values));
    if (!player.version && values.version) player.version = values.version;
    touch(player, event);
  }

  const list = [...players.values()]
    .map(summarize)
    .sort((a, b) => (b.lastAt ?? '').localeCompare(a.lastAt ?? ''));

  return { players: list, totals: { connects: connects.length, games: gameEnds.length } };
}

async function getPlayersFromPosthog(range) {
  const { connects, gameEnds, dataById } = await getPosthogPlayerEvents(range);
  return buildPlayers(connects, gameEnds, dataById);
}

module.exports = { getPlayers, getPlayersFromPosthog, buildPlayers };
