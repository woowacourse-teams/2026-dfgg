import { useEffect, useMemo, useState } from 'react';

import {
  canonicalItemId,
  type DDragonData,
  loadDDragon,
  toPatch,
} from '../../../packages/shared/ddragon';
import type {
  RecommendationV3Request,
  RecommendationV3Response,
} from '../../../packages/shared/types';
import type { Lineup } from '../electron/types';
import { requestBuildV3 } from './requestBuildV3';
import { useLineup } from './useLineup';
import { isPicked, resolveLineup } from './useRecommendation';

/** 지금은 랭크 조회 기능이 없어 고정값을 보낸다. 나중에 LCU 랭크 정보로 교체한다. */
const TIER = 'PLATINUM';

/** 요청이 실패했을 때 다시 시도하기까지의 간격. */
const RETRY_MS = 5000;

/**
 * 재료템·시작 아이템이 섞여 들어가면 백엔드가 조합을 못 알아보고 추천을
 * 2개로 줄여버리는 게 확인됐다. 전설급 이상 완성 아이템만 골라 보낸다.
 */
function completedCoreItemIds(myItemIds: number[], ddragon: DDragonData): number[] {
  return myItemIds.filter((id) => ddragon.coreItemIds.has(canonicalItemId(id)));
}

function toRequestV3(lineup: Lineup, ddragon: DDragonData): RecommendationV3Request | null {
  const resolved = resolveLineup(lineup, ddragon);
  if (!resolved) return null;
  return {
    myChampion: { name: resolved.myKey, position: resolved.myPosition },
    purchasedItemIds: completedCoreItemIds(lineup.myItemIds, ddragon),
    allies: resolved.allies,
    enemies: resolved.enemies,
    tier: TIER,
    patch: toPatch(ddragon.version),
  };
}

/**
 * 2번 추천 방식(v3). v2와 달리 한 판에 한 번이 아니라, 아이템을 살 때마다
 * (purchasedItemIds가 바뀔 때마다) 다시 요청해서 다음 추천을 받는다.
 * 1번(useRecommendation)과 별개로 계속 돌아간다 — 두 방식을 같이 켜두고
 * 버튼으로 어느 결과를 보여줄지만 바꿀 수 있게 하기 위해서다.
 */
export function useRecommendationV3() {
  const { lineup, status, windowMode } = useLineup();
  const [ddragon, setDDragon] = useState<DDragonData | null>(null);
  const [result, setResult] = useState<RecommendationV3Response | null>(null);
  const [error, setError] = useState('');
  const [ddragonError, setDdragonError] = useState('');
  // 이 결과/에러가 어느 "판 + 구매 목록" 조합에서 나왔는지. 판이 바뀌거나
  // 아이템을 새로 사서 조합이 달라지면 자연히 안 맞아져서, 지난 결과·에러가
  // 새 조합에 잘못 보이는 일 없이 effect 밖에서 파생값으로 걸러진다.
  const [resultKey, setResultKey] = useState<string | null>(null);
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const [retryTick, setRetryTick] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    loadDDragon(controller.signal)
      .then(setDDragon)
      .catch(() => {
        if (!controller.signal.aborted) setDdragonError('챔피언 정보를 불러오지 못했어요.');
      });
    return () => controller.abort();
  }, []);

  const request = useMemo(
    () => (lineup && ddragon ? toRequestV3(lineup, ddragon) : null),
    [lineup, ddragon],
  );

  const sessionId = lineup?.sessionId ?? -1;
  // 폴링 사이에 순서가 달라져도 같은 요청으로 취급하도록 정렬해서 비교한다.
  const purchasedKey = request ? [...request.purchasedItemIds].sort((a, b) => a - b).join(',') : '';
  const fetchKey = `${sessionId}:${purchasedKey}`;

  useEffect(() => {
    if (!request) return;
    if (resultKey === fetchKey) return;

    const controller = new AbortController();
    let retryTimer: ReturnType<typeof setTimeout> | undefined;

    // 2번 방식이 구매 개수에 따라 몇 개를 돌려주는지 확인하려고 남겨둔 로그다.
    // 원인이 확인되면 지운다.
    console.log('[recommend-v3] 요청', {
      purchasedItemIds: request.purchasedItemIds,
      myChampion: request.myChampion,
    });

    requestBuildV3(request, controller.signal)
      .then((response) => {
        console.log('[recommend-v3] 응답', {
          servedBy: response.servedBy,
          count: response.recommendedItems.length,
          items: response.recommendedItems.map((item) => `${item.id} ${item.name}`),
        });
        // 성공한 뒤에 기록해야 실패한 요청이 재시도를 막지 않는다.
        setResultKey(fetchKey);
        setResult(response);
        window.umami?.track('desktop-recommend-v3-success');
      })
      .catch((cause) => {
        if (controller.signal.aborted) return;
        console.error(cause);
        setErrorKey(fetchKey);
        setError('추천을 불러오지 못했어요. 다시 시도 중...');
        window.umami?.track('desktop-recommend-v3-fail', {
          reason: cause instanceof Error ? cause.message : 'unknown',
        });
        retryTimer = setTimeout(() => setRetryTick((tick) => tick + 1), RETRY_MS);
      });

    return () => {
      controller.abort();
      if (retryTimer) clearTimeout(retryTimer);
    };
  }, [request, fetchKey, retryTick, resultKey]);

  const allyPicked = lineup?.allies.filter(isPicked).length ?? 0;
  const enemyPicked = lineup?.enemies.filter(isPicked).length ?? 0;

  const inSession = lineup !== null;
  const resultIsCurrent = resultKey === fetchKey;
  const errorIsCurrent = errorKey === fetchKey;
  const loading = inSession && Boolean(request) && !resultIsCurrent && !errorIsCurrent;

  return {
    lineup,
    status,
    windowMode,
    ddragon,
    request,
    result: inSession && resultIsCurrent ? result : null,
    error: ddragonError || (inSession && errorIsCurrent ? error : ''),
    loading: inSession ? loading : false,
    allyPicked,
    enemyPicked,
  };
}
