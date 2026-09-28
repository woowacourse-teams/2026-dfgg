import type { GameflowPhase } from '../../shared/types';
import { fetchItemRecommendations } from '../api/recommendations';
import { getLcuState, onPhaseChange, setRecommendations } from '../lcu/state';
import { getDDragonData } from './ddragon';
import { getActivePlayer, getPlayerList } from './endpoints';
import { buildRecommendationBody, extractItemIds } from './payload';
import { fetchGameVersion } from '../lcu/service';
import { recordChampion, recordLiveError, recordPurchases } from '../analytics/gameTracker';

const POLL_INTERVAL_MS = 2000;
// 추천 요청이 실패하면 서버 부하를 줄이려고 재시도 간격을 늘린다. (2초 → 4초 → … → 30초)
const MAX_RETRY_INTERVAL_MS = 30_000;

let recommendFailures = 0;

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
// 그래서 신발은 한 번 산 것이 보이면 게임이 끝날 때까지 가진 것으로 본다.
function updatePurchaseOrder(itemIds: number[], bootsItemIds: Set<number>) {
  const current = new Set(itemIds);
  for (const id of purchasedItemIds) {
    if (!current.has(id) && !bootsItemIds.has(id)) purchasedItemIds.delete(id);
  }
  for (const id of current) purchasedItemIds.add(id);
}

// 인게임 정보와 소환사 id 정보를 가져와서 필요하면 백엔드에 아이템 요청 보내기
async function tick() {
  try {
    if (myRiotId === null) {
      const me = await getActivePlayer();
      myRiotId = me?.riotId ?? me?.summonerName ?? null;
    }

    if (patch === null) {
      const currentPatch = await fetchGameVersion();
      patch = currentPatch;
    }

    const { championNames, componentItemIds, bootsItemIds } = await getDDragonData();

    const players = await getPlayerList();
    const myPlayers = players?.find(
      (player) => player.riotId === myRiotId || player.summonerName === myRiotId,
    );

    if (myPlayers) {
      const itemIds = extractItemIds(myPlayers, componentItemIds);

      if (lastItemIds === null || !sameItems(itemIds, lastItemIds)) {
        if (!players || !myRiotId || !patch || !championNames || !componentItemIds) return;

        if (lastItemIds !== null) {
          const newItemIds = itemIds.filter((id) => !purchasedItemIds.has(id));
          recordPurchases(newItemIds, getLcuState().recommendations);
        }
        updatePurchaseOrder(itemIds, bootsItemIds);

        console.log('아이템 변경', itemIds);

        const liveInfo = { players, myRiotId, patch, championNames, purchasedItemIds };

        // 백엔드에 전달
        const body = buildRecommendationBody(liveInfo);
        if (!body) return;
        recordChampion(body.myChampion);
        let result;
        try {
          result = await fetchItemRecommendations(body);
        } catch (error) {
          // live API 실패(게임 로딩 중 등)와 구분해서 추천 실패에만 간격을 늘린다.
          recommendFailures++;
          throw error;
        }
        recommendFailures = 0;
        setRecommendations(result.recommendedItems, purchasedItemIds.size);

        lastItemIds = itemIds;
      }
    }
  } catch (error) {
    console.debug('live 조회 실패', error);
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
  recommendFailures = 0;
  myRiotId = null;
  patch = null;
  purchasedItemIds.clear();
}
