import type { GameflowPhase } from '../../shared/types';
import { fetchItemRecommendations } from '../api/recommendations';
import { getLcuState, onPhaseChange, setRecommendations } from '../lcu/state';
import { getDDragonData } from './ddragon';
import { getActivePlayer, getGameStats, getPlayerList } from './endpoints';
import { buildRecommendationBody, extractItemIds, toInventory } from './payload';
import { fetchGameVersion } from '../lcu/service';
import {
  recordChampion,
  recordLiveError,
  recordPurchases,
  recordSkip,
} from '../analytics/gameTracker';
import { reportError } from '../sentry';

const POLL_INTERVAL_MS = 2000;
// 추천 요청이 실패하면 서버 부하를 줄이려고 재시도 간격을 늘린다. (2초 → 4초 → … → 30초)
const MAX_RETRY_INTERVAL_MS = 30_000;

// 하위 아이템 묶음은 골드에 따라 달라진다. 다만 골드는 매초 오르므로 오를 때마다 묻지 않는다.
// 마지막 요청보다 이만큼 더 모였고, 이 시간이 지났을 때만 다시 묻는다.
// ponytail: 고정 기준이라 귀환 직후처럼 꼭 필요한 순간을 놓칠 수 있다. 상점 열림을 알 수 있으면 그때 묻는 게 낫다.
const GOLD_REFRESH_STEP = 300;
const GOLD_REFRESH_MIN_MS = 10_000;
const SECOND_MS = 1000;

let recommendFailures = 0;
// 마지막으로 추천을 받은 상태. 인벤토리가 바뀌거나 골드가 충분히 모이면 다시 묻는다.
let lastRequestKey: string | null = null;
let lastRequestGold = 0;
let lastRequestAt = 0;

function nextPollInterval() {
  if (recommendFailures === 0) return POLL_INTERVAL_MS;
  return Math.min(POLL_INTERVAL_MS * 2 ** recommendFailures, MAX_RETRY_INTERVAL_MS);
}

let timer: NodeJS.Timeout | null = null;
let polling: boolean = false;
let lastItemIds: number[] | null = null;
const purchasedItemIds = new Set<number>();
let myRiotId: string | null = null;
let patch: string | null = null;

// phase가 InProgress면 live polling 시작하기
function syncWithPhase(phase: GameflowPhase | null) {
  if (phase === 'InProgress') startLivePolling();
  else stopLivePolling();
}

// 앱 첫 시작 시, phase 확인하고 구독하기
export function initLivePolling() {
  syncWithPhase(getLcuState().phase);
  onPhaseChange(syncWithPhase);
}

// 이전 아이템과 현재 아이템이 같은지 비교하기
function sameItems(a: number[], b: number[]) {
  if (a.length !== b.length) return false;
  const sortedA = [...a].sort((x, y) => x - y);
  const sortedB = [...b].sort((x, y) => x - y);
  return sortedA.every((id, i) => id === sortedB[i]);
}

// 구매 순서를 유지하면서 아이템 리스트 업데이트 하기
// 원딜은 역할 퀘스트를 깨면 신발이 인벤토리 밖 전용 칸으로 옮겨지고 Live API 에서도 사라진다.
// 그래서 원딜일 경우 신발은 한 번 산 것이 보이면 게임이 끝날 때까지 가진 것으로 본다.
function updatePurchaseOrder(itemIds: number[], bootsItemIds: Set<number>, isBottom: boolean) {
  const current = new Set(itemIds);
  for (const id of purchasedItemIds) {
    const keepBoots = isBottom && bootsItemIds.has(id);
    if (!current.has(id) && !keepBoots) purchasedItemIds.delete(id);
  }
  for (const id of current) purchasedItemIds.add(id);
}

/** 인벤토리나 산 코어가 바뀌었거나, 골드가 충분히 모였으면 다시 묻는다. */
function shouldRequest(key: string, gold: number, now: number) {
  if (key !== lastRequestKey) return true;
  return gold - lastRequestGold >= GOLD_REFRESH_STEP && now - lastRequestAt >= GOLD_REFRESH_MIN_MS;
}

// 인게임 정보와 소환사 id 정보를 가져와서 필요하면 백엔드에 아이템 요청 보내기
async function tick() {
  try {
    // 골드는 매번 새로 읽어야 해서 내 정보도 매번 받는다.
    const active = await getActivePlayer();
    if (myRiotId === null) myRiotId = active?.riotId ?? active?.summonerName ?? null;
    const gold = active?.currentGold ?? 0;

    if (patch === null) {
      const currentPatch = await fetchGameVersion();
      patch = currentPatch;
    }

    const { championNames, componentItemIds, bootsItemIds } = await getDDragonData();

    const players = await getPlayerList();
    const myPlayers = players?.find(
      (player) => player.riotId === myRiotId || player.summonerName === myRiotId,
    );
    // 여기서 막히면 추천을 묻지도 못한다. 실패 기록도 안 남아서 이유를 따로 센다.
    if (!patch) return recordSkip('noPatch');
    if (!championNames || !componentItemIds) return recordSkip('noDDragon');
    if (!myPlayers || !players || !myRiotId) return recordSkip('noSelf');

    // 완성 아이템이 바뀌면 구매 순서를 갱신한다 (v3 와 같다)
    const itemIds = extractItemIds(myPlayers, componentItemIds);
    if (lastItemIds === null || !sameItems(itemIds, lastItemIds)) {
      if (lastItemIds !== null) {
        const newItemIds = itemIds.filter((id) => !purchasedItemIds.has(id));
        const { recommendations, situationalItems } = getLcuState();
        recordPurchases(newItemIds, recommendations, situationalItems);
      }
      updatePurchaseOrder(itemIds, bootsItemIds, myPlayers.position === 'BOTTOM');
      lastItemIds = itemIds;
    }

    const now = Date.now();
    const key = JSON.stringify([toInventory(myPlayers), [...purchasedItemIds]]);
    if (!shouldRequest(key, gold, now)) return;

    const stats = await getGameStats();
    const body = buildRecommendationBody({
      players,
      myRiotId,
      patch,
      championNames,
      purchasedItemIds,
      availableGold: gold,
      gameTimeMs: (stats?.gameTime ?? 0) * SECOND_MS,
    });
    if (!body) return;
    recordChampion(body.myChampion);

    let result;
    try {
      result = await fetchItemRecommendations(body);
    } catch {
      // live API 실패(게임 로딩 중 등)와 구분해서 추천 실패에만 간격을 늘린다.
      // 추천 실패는 fetchItemRecommendations 가 기록·보고하므로 live 실패로 다시 세지 않는다.
      recommendFailures++;
      return;
    }
    recommendFailures = 0;
    lastRequestKey = key;
    lastRequestGold = gold;
    lastRequestAt = now;

    setRecommendations({
      items: result.recommendedItems,
      situationalItems: result.situationalItems ?? [],
      purchasedCount: purchasedItemIds.size,
      availableGold: body.availableGold,
    });
  } catch (error) {
    console.debug('live 조회 실패', error);
    reportError('live-poll', error);
    recordLiveError();
  } finally {
    if (polling) timer = setTimeout(tick, nextPollInterval());
  }
}

// 라이브 폴링 시작 함수
function startLivePolling() {
  if (polling) return;
  polling = true;
  tick();
}

// 라이브 폴링 중단 함수
export function stopLivePolling() {
  polling = false;

  if (timer !== null) {
    clearTimeout(timer);
    timer = null;
  }

  lastItemIds = null;
  lastRequestKey = null;
  lastRequestGold = 0;
  lastRequestAt = 0;
  recommendFailures = 0;
  myRiotId = null;
  patch = null;
  purchasedItemIds.clear();
}
