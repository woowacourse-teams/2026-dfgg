import { useEffect, useMemo, useState } from 'react';

import { type DDragonData, loadDDragon } from '../../../packages/shared/ddragon';
import type {
  Champion,
  Position,
  RecommendationRequest,
  RecommendationResponse,
} from '../../../packages/shared/types';
import type { Lineup, LineupSlot } from '../electron/types';
import { requestBuild } from './requestBuild';
import { useLineup } from './useLineup';

/** 백엔드는 아군·적군의 position을 쓰지 않지만 형식 검증은 한다. */
const PLACEHOLDER_POSITION: Position = 'MID';

/** 요청이 실패했을 때 다시 시도하기까지의 간격. */
const RETRY_MS = 5000;

export const isPicked = (slot: LineupSlot) => slot.championId > 0 || slot.championName !== null;

/**
 * 밴픽은 숫자 id로, 인게임은 한글명으로 오므로 둘 다 받아 챔피언을 찾는다.
 */
export function findChampion(ddragon: DDragonData, id: number, name: string | null) {
  return ddragon.byChampionId.get(id) ?? (name ? ddragon.byName.get(name) : undefined);
}

export interface ResolvedLineup {
  myKey: string;
  myPosition: Position;
  allies: Champion[];
  enemies: Champion[];
}

/**
 * 밴픽 현황을 백엔드가 알아들을 영문 키·포지션으로 정리한다. 아군 4명(나 제외) +
 * 적군 5명이 모두 확정돼야 하므로, 한 명이라도 비어 있으면 null을 돌려준다.
 * v2, v3 요청이 모두 같은 조합 정보를 쓰므로 여기서 한 번만 만든다.
 */
export function resolveLineup(lineup: Lineup, ddragon: DDragonData): ResolvedLineup | null {
  const riotKeyOf = (slot: LineupSlot) =>
    findChampion(ddragon, slot.championId, slot.championName)?.riotKey;

  const myKey = findChampion(ddragon, lineup.myChampionId, lineup.myChampionName)?.riotKey;
  if (!myKey || !lineup.myPosition) return null;

  const allies: Champion[] = [];
  for (const slot of lineup.allies) {
    // 나는 allies에서 뺀다. 밴픽은 cellId로, 인게임은 cellId가 없으니 챔피언으로 거른다.
    const isMe =
      lineup.source === 'champ-select'
        ? slot.cellId === lineup.myCellId
        : slot.championName === lineup.myChampionName;
    if (isMe) continue;
    const key = riotKeyOf(slot);
    if (!key) return null;
    allies.push({ name: key, position: slot.position ?? PLACEHOLDER_POSITION });
  }

  const enemies: Champion[] = [];
  for (const slot of lineup.enemies) {
    const key = riotKeyOf(slot);
    if (!key) return null;
    enemies.push({ name: key, position: slot.position ?? PLACEHOLDER_POSITION });
  }

  if (allies.length !== 4 || enemies.length !== 5) return null;

  return { myKey, myPosition: lineup.myPosition, allies, enemies };
}

/**
 * 백엔드가 요구하는 형태로 바꾼다.
 */
function toRequest(lineup: Lineup, ddragon: DDragonData): RecommendationRequest | null {
  const resolved = resolveLineup(lineup, ddragon);
  if (!resolved) return null;
  return {
    myChampion: { name: resolved.myKey, position: resolved.myPosition },
    allies: resolved.allies,
    enemies: resolved.enemies,
  };
}

/** 밴픽 현황을 추천 결과까지 이어주는 훅. 메인 창과 오버레이가 함께 쓴다. */
export function useRecommendation() {
  const { lineup, status, windowMode } = useLineup();
  const [ddragon, setDDragon] = useState<DDragonData | null>(null);
  const [result, setResult] = useState<RecommendationResponse | null>(null);
  const [error, setError] = useState('');
  // 판과 무관한 에러라 sessionId 게이팅을 받지 않는다. 챔피언 데이터 자체가
  // 없으면 어떤 판이든 추천을 만들 수 없어 계속 보여줘야 한다.
  const [ddragonError, setDdragonError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    loadDDragon(controller.signal)
      .then(setDDragon)
      .catch(() => {
        if (!controller.signal.aborted) {
          setDdragonError('챔피언 정보를 불러오지 못했어요.');
          window.umami?.track('desktop-ddragon-load-failed');
        }
      });
    return () => controller.abort();
  }, []);

  const request = useMemo(
    () => (lineup && ddragon ? toRequest(lineup, ddragon) : null),
    [lineup, ddragon],
  );

  // 추천을 이미 받아둔 판의 번호. 한 판에 한 번만 요청한다.
  // 렌더에서도 읽어야 해서(직전 판 아이템 감추기) ref 가 아니라 state 로 둔다.
  const [fetchedSession, setFetchedSession] = useState(-1);
  // error가 어느 판 것인지 같이 기록한다. 다음 판이 시작되면(sessionId 변경)
  // 이 값이 안 맞아져서 effect로 따로 지우지 않아도 자연히 안 보이게 된다.
  const [errorSession, setErrorSession] = useState(-1);
  // 실패했을 때 다시 시도하게 만드는 신호. 조합이 그대로여도 effect를 다시 돌린다.
  const [retryTick, setRetryTick] = useState(0);

  const sessionId = lineup?.sessionId ?? -1;

  useEffect(() => {
    if (!request) return;
    // 밴픽에서 한 번 받았으면 게임이 시작돼도 다시 분석하지 않는다.
    // 조합이 같은데 API 응답 형태만 바뀌어(숫자 id ↔ 한글명) 재요청되던 문제를 막는다.
    if (fetchedSession === sessionId) return;

    const controller = new AbortController();
    let retryTimer: ReturnType<typeof setTimeout> | undefined;

    requestBuild(request, controller.signal)
      .then((response) => {
        // 성공한 뒤에 기록해야 실패한 요청이 재시도를 막지 않는다.
        setFetchedSession(sessionId);
        setResult(response);
        window.umami?.track('desktop-recommend-success');
      })
      .catch((cause) => {
        if (controller.signal.aborted) return;
        console.error(cause);
        setErrorSession(sessionId);
        setError('추천을 불러오지 못했어요. 다시 시도 중...');
        window.umami?.track('desktop-recommend-fail', {
          reason: cause instanceof Error ? cause.message : 'unknown',
        });
        retryTimer = setTimeout(() => setRetryTick((tick) => tick + 1), RETRY_MS);
      });

    return () => {
      controller.abort();
      if (retryTimer) clearTimeout(retryTimer);
    };
  }, [request, sessionId, retryTick, fetchedSession]);

  const allyPicked = lineup?.allies.filter(isPicked).length ?? 0;
  const enemyPicked = lineup?.enemies.filter(isPicked).length ?? 0;

  // 판이 끝났거나(lineup null) 다음 판이 시작됐는데(sessionId 변경) 아직 새 추천을
  // 못 받았으면, 직전 판의 아이템을 보여주면 안 된다. 다음 판 정보로 오해하게 된다.
  // state를 지우는 대신 파생값으로 처리해 effect 안에서 setState 하지 않는다.
  const inSession = lineup !== null;
  const resultIsCurrent = inSession && fetchedSession === sessionId;
  const errorIsCurrent = inSession && errorSession === sessionId;
  // 요청은 있는데 이번 판 결과를 아직 못 받았고 에러도 없으면 분석 중이다.
  // state 로 들고 있으면 effect 안에서 동기 setState 를 하게 되어 파생값으로 둔다.
  const loading = inSession && Boolean(request) && !resultIsCurrent && !errorIsCurrent;

  return {
    lineup,
    status,
    windowMode,
    ddragon,
    request,
    // 이번 판에서 받은 추천일 때만 보여준다.
    result: resultIsCurrent ? result : null,
    // 이번 판에서 난 에러일 때만 보여준다. 지난 판 에러가 다음 판에 남아있지 않는다.
    error: ddragonError || (errorIsCurrent ? error : ''),
    loading: inSession ? loading : false,
    allyPicked,
    enemyPicked,
  };
}
