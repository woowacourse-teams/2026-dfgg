import type { GameflowPhase } from '../../shared/types';
import { fetchItemRecommendations } from '../api/recommendations';
import { getLcuState, onPhaseChange, setRecommendations } from '../lcu/state';
import { getDDragonData } from './ddragon';
import { getActivePlayer, getPlayerList } from './endpoints';
import { buildRecommendationBody, extractItemIds } from './payload';
import { fetchGameVersion } from '../lcu/service';

const POLL_INTERVAL_MS = 2000;

let timer: NodeJS.Timeout | null = null;
let polling: boolean = false;
let lastItemIds: number[] | null = null;
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

    const { championNames, componentItemIds } = await getDDragonData();

    const players = await getPlayerList();
    const myPlayers = players?.find(
      (player) => player.riotId === myRiotId || player.summonerName === myRiotId,
    );

    if (myPlayers) {
      const itemIds = extractItemIds(myPlayers, componentItemIds);

      if (lastItemIds === null || !sameItems(itemIds, lastItemIds)) {
        if (!players || !myRiotId || !patch || !championNames || !componentItemIds) return;

        console.log('아이템 변경', itemIds);

        const liveInfo = { players, myRiotId, patch, championNames, componentItemIds };

        // 백엔드에 전달
        const body = buildRecommendationBody(liveInfo);
        if (!body) return;
        const result = await fetchItemRecommendations(body);
        setRecommendations(result.recommendedItems);

        lastItemIds = itemIds;
      }
    }
  } catch (error) {
    console.debug('live 조회 실패', error);
  } finally {
    if (polling) timer = setTimeout(tick, POLL_INTERVAL_MS);
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
  myRiotId = null;
  patch = null;
}
