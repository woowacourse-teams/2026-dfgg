import type { GameflowPhase, RecommendedItem } from '../../shared/types';
import { fetchEndOfGameStats, fetchGameflowSession, fetchQueueName } from '../lcu/service';
import { getLcuState, onPhaseChange } from '../lcu/state';
import { getIdentity, type Identity } from './identity';
import { Stopwatch } from './stopwatch';
import { readStore, updateStore, type PendingEvent } from './store';
import { enqueue, trackEvent } from './umami';

const SNAPSHOT_INTERVAL_MS = 60_000;
// 게임은 끝났지만 결과 화면 전인 phase. 이때는 게임을 끝내지 않는다.
const POST_GAME_PHASES: (GameflowPhase | null)[] = ['WaitingForStats', 'PreEndOfGame', 'Reconnect'];

type Game = {
  identity: Identity | null;
  gameId?: number;
  queue?: string;
  champion?: string;
  position?: string;
  overlayExpanded: Stopwatch;
  overlayCollapsed: Stopwatch;
  collapseToggles: number;
  recommendSuccess: number;
  recommendError: number;
  liveError: number;
  purchases: number;
  // 산 아이템이 직전 추천 목록에 있었던 횟수
  followed: number;
  followedTop1: number;
};

let game: Game | null = null;
// 오버레이 접힘 상태는 게임이 바뀌어도 유지된다.
let overlayCollapsed = false;
let timer: NodeJS.Timeout | null = null;

function toEvent(current: Game, result: string): PendingEvent {
  const overlayExpandedSec = current.overlayExpanded.seconds();
  const overlayCollapsedSec = current.overlayCollapsed.seconds();

  return {
    name: 'desktop-game-end',
    id: current.identity?.puuid,
    data: {
      riotId: current.identity?.riotId,
      gameId: current.gameId,
      queue: current.queue,
      champion: current.champion,
      position: current.position,
      result,
      overlaySec: overlayExpandedSec + overlayCollapsedSec,
      overlayExpandedSec,
      overlayCollapsedSec,
      collapseToggles: current.collapseToggles,
      recommendSuccess: current.recommendSuccess,
      recommendError: current.recommendError,
      liveError: current.liveError,
      purchases: current.purchases,
      followed: current.followed,
      followedTop1: current.followedTop1,
    },
  };
}

// 오버레이는 InProgress일 때만 보이므로 그때만 시간을 잰다.
function syncOverlay() {
  if (!game) return;
  const shown = getLcuState().phase === 'InProgress';

  if (shown && !overlayCollapsed) game.overlayExpanded.start();
  else game.overlayExpanded.stop();

  if (shown && overlayCollapsed) game.overlayCollapsed.start();
  else game.overlayCollapsed.stop();
}

async function startGame() {
  // 재접속으로 다시 InProgress가 되면 이어서 잰다.
  if (game) return syncOverlay();

  const current: Game = {
    identity: getIdentity(),
    overlayExpanded: new Stopwatch(),
    overlayCollapsed: new Stopwatch(),
    collapseToggles: 0,
    recommendSuccess: 0,
    recommendError: 0,
    liveError: 0,
    purchases: 0,
    followed: 0,
    followedTop1: 0,
  };
  game = current;
  syncOverlay();

  // 게임 도중 강제 종료에 대비해 1분마다 저장해 둔다.
  timer = setInterval(() => {
    if (game) updateStore({ openGame: toEvent(game, 'crash') });
  }, SNAPSHOT_INTERVAL_MS);

  try {
    const session = await fetchGameflowSession();
    if (!session) return;

    const queueId = session.gameData.queue.id;
    current.gameId = session.gameData.gameId;
    current.queue = (await fetchQueueName(queueId)) ?? String(queueId);
  } catch {
    // 큐 정보 없이 계속한다.
  }
}

function closeGame() {
  const current = game;
  if (!current) return null;

  current.overlayExpanded.stop();
  current.overlayCollapsed.stop();
  game = null;

  if (timer !== null) clearInterval(timer);
  timer = null;
  updateStore({ openGame: undefined });

  return current;
}

// 결과 화면까지 왔으면 승패를 붙여서 보낸다.
async function finishGame(reachedEndOfGame: boolean) {
  const current = closeGame();
  if (!current) return;

  let result = 'unknown';
  if (reachedEndOfGame) {
    try {
      const stats = await fetchEndOfGameStats();
      const myTeam = stats?.teams.find((team) => team.isPlayerTeam);
      if (myTeam) result = myTeam.isWinningTeam ? 'win' : 'lose';
    } catch {
      // 승패 없이 보낸다.
    }
  }

  const event = toEvent(current, result);
  trackEvent(event.name, event.data, event.id);
}

function handlePhase(phase: GameflowPhase | null) {
  if (phase === 'InProgress') {
    startGame();
    return;
  }
  if (!game) return;

  if (phase === 'EndOfGame') {
    finishGame(true);
    return;
  }
  if (POST_GAME_PHASES.includes(phase)) {
    syncOverlay();
    return;
  }
  // 다시하기·튕김 등으로 결과 화면 없이 빠져나온 경우
  finishGame(false);
}

export function initGameTracking() {
  onPhaseChange(handlePhase);
}

// 지난 실행이 게임 도중 비정상 종료됐으면 보낼 목록에 넣는다.
export function recoverLastGame() {
  const { openGame } = readStore();
  if (!openGame) return;

  enqueue(openGame);
  updateStore({ openGame: undefined });
}

// 게임 도중 앱을 끄면 다음 실행 때 보낸다.
export function endGameTracking() {
  const current = closeGame();
  if (current) enqueue(toEvent(current, 'app-quit'));
}

export function setOverlayCollapsed(collapsed: boolean) {
  if (game && overlayCollapsed !== collapsed) game.collapseToggles++;
  overlayCollapsed = collapsed;
  syncOverlay();
}

export function recordRecommendation(success: boolean) {
  if (!game) return;
  if (success) game.recommendSuccess++;
  else game.recommendError++;
}

export function recordLiveError() {
  if (game) game.liveError++;
}

export function recordChampion(champion: { name: string; position: string }) {
  if (!game || game.champion) return;
  game.champion = champion.name;
  game.position = champion.position;
}

// 새로 산 아이템이 직전 추천 목록에 있었는지 센다.
export function recordPurchases(newItemIds: number[], recommendations: RecommendedItem[] | null) {
  if (!game) return;

  for (const itemId of newItemIds) {
    game.purchases++;
    const rank = recommendations?.findIndex((item) => item.id === itemId) ?? -1;
    if (rank >= 0) game.followed++;
    if (rank === 0) game.followedTop1++;
  }
}
