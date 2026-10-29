import type {
  GameflowPhase,
  OverlayClickTarget,
  RecommendedItem,
  SituationalItem,
} from '../../shared/types';
import type { ApiErrorKind } from '../api/client';
import { fetchEndOfGameStats, fetchGameflowSession, fetchQueueName } from '../lcu/service';
import { getLcuState, onPhaseChange } from '../lcu/state';
import { getIdentity, type Identity } from './identity';
import { Stopwatch } from './stopwatch';
import { readStore, updateStore, type PendingEvent } from './store';
import { enqueue, trackEvent } from './umami';
import { reportError } from '../sentry';
import { BrowserWindow, screen } from 'electron';
import { PerfSampler } from './perfSampler';
import { isRecording } from '../highlight/highlight';
import { getRecorderPid } from '../highlight/recoder';

const SNAPSHOT_INTERVAL_MS = 60_000;
// 게임은 끝났지만 결과 화면 전인 phase. 이때는 게임을 끝내지 않는다.
const POST_GAME_PHASES: (GameflowPhase | null)[] = ['WaitingForStats', 'PreEndOfGame', 'Reconnect'];

type Game = {
  identity: Identity | null;
  gameId?: number;
  // 큐 이름은 클라이언트 언어마다 달라서, 집계는 queueId 로 한다.
  queueId?: number;
  queue?: string;
  champion?: string;
  position?: string;
  overlayExpanded: Stopwatch;
  overlayCollapsed: Stopwatch;
  collapseToggles: number;
  startedAt: number;
  recommendSuccess: number;
  recommendError: number;
  recommendErrorByKind: Record<ApiErrorKind, number>;
  // 응답은 왔지만 추천이 비어 있던 횟수
  recommendEmpty: number;
  recommendMsTotal: number;
  recommendMaxMs: number;
  // 게임 시작부터 첫 추천을 받기까지 (못 받았으면 undefined)
  firstRecommendSec?: number;
  liveError: number;
  // 추천을 묻지도 못하고 넘어간 틱. 한 판 내내 쌓이면 추천이 한 번도 안 간 것이다.
  skips: Record<SkipReason, number>;
  purchases: number;
  // 산 아이템이 직전 추천 목록에 있었던 횟수
  followed: number;
  followedTop1: number;
  // 산 아이템이 직전 상황템 목록에 있었던 횟수
  followedSituational: number;
  overlayClicks: Partial<Record<OverlayClickTarget, number>>;
  overlayMoves: number;
  perf: PerfSampler;
};

let game: Game | null = null;
// 오버레이 접힘 상태는 게임이 바뀌어도 유지된다.
let overlayCollapsed = false;
let timer: NodeJS.Timeout | null = null;
let overlayWindow: BrowserWindow | null = null;

export function setOverlayWindow(win: BrowserWindow) {
  overlayWindow = win;
  win.on('moved', () => {
    if (game) game.overlayMoves++;
  });
}

export function recordOverlayClick(target: OverlayClickTarget) {
  if (!game) return;
  game.overlayClicks[target] = (game.overlayClicks[target] ?? 0) + 1;
}

// 게임이 끝날 때 오버레이가 모니터의 어디에 있었는지 (비율이라 해상도와 상관없이 비교 가능)
function overlayPosition() {
  if (!overlayWindow || overlayWindow.isDestroyed()) return {}; // 창이 없으면 위치 정보 없이 보냄
  const b = overlayWindow.getBounds();
  const d = screen.getDisplayMatching(b).bounds;
  const cx = (b.x + b.width / 2 - d.x) / d.width;
  const cy = (b.y + b.height / 2 - d.y) / d.height;
  return {
    overlayX: Math.round(cx * 100),
    overlayY: Math.round(cy * 100),
    overlayArea: `${cy < 0.5 ? 'top' : 'bottom'}-${cx < 0.5 ? 'left' : 'right'}`,
    displayW: d.width,
    displayH: d.height,
  };
}

function toEvent(current: Game, result: string): PendingEvent {
  const overlayExpandedSec = current.overlayExpanded.seconds();
  const overlayCollapsedSec = current.overlayCollapsed.seconds();

  return {
    name: 'desktop-game-end',
    id: current.identity?.puuid,
    data: {
      riotId: current.identity?.riotId,
      gameId: current.gameId,
      queueId: current.queueId,
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
      recommendErrorTimeout: current.recommendErrorByKind.timeout,
      recommendError4xx: current.recommendErrorByKind['4xx'],
      recommendError5xx: current.recommendErrorByKind['5xx'],
      recommendErrorNetwork: current.recommendErrorByKind.network,
      recommendEmpty: current.recommendEmpty,
      recommendAvgMs: avgRecommendMs(current),
      recommendMaxMs: current.recommendMaxMs || undefined,
      firstRecommendSec: current.firstRecommendSec,
      liveError: current.liveError,
      skipNoSelf: current.skips.noSelf,
      skipNoPatch: current.skips.noPatch,
      skipNoDDragon: current.skips.noDDragon,
      purchases: current.purchases,
      followed: current.followed,
      followedTop1: current.followedTop1,
      followedSituational: current.followedSituational,
      overlayClicks: Object.values(current.overlayClicks).reduce((a, b) => a + b, 0),
      ...Object.fromEntries(
        Object.entries(current.overlayClicks).map(([k, v]) => [`click_${k}`, v]),
      ),
      overlayMoves: current.overlayMoves,
      ...overlayPosition(),
      ...current.perf.summary(),
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
    startedAt: Date.now(),
    overlayExpanded: new Stopwatch(),
    overlayCollapsed: new Stopwatch(),
    collapseToggles: 0,
    recommendSuccess: 0,
    recommendError: 0,
    recommendErrorByKind: { timeout: 0, '4xx': 0, '5xx': 0, network: 0 },
    recommendEmpty: 0,
    recommendMsTotal: 0,
    recommendMaxMs: 0,
    liveError: 0,
    skips: { noSelf: 0, noPatch: 0, noDDragon: 0 },
    purchases: 0,
    followed: 0,
    followedTop1: 0,
    followedSituational: 0,
    overlayClicks: {},
    overlayMoves: 0,
    perf: new PerfSampler(),
  };
  game = current;
  current.perf.start(isRecording, getRecorderPid);
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
    current.queueId = queueId;
    current.queue = (await fetchQueueName(queueId)) ?? String(queueId);
  } catch (error) {
    // 큐 정보 없이 계속한다.
    reportError('game-tracker-queue', error);
  }
}

function closeGame() {
  const current = game;
  if (!current) return null;

  current.overlayExpanded.stop();
  current.overlayCollapsed.stop();
  current.perf.stop();
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
      const myTeam = stats?.teams?.find((team) => team.isPlayerTeam);
      if (myTeam) result = myTeam.isWinningTeam ? 'win' : 'lose';
    } catch (error) {
      // 승패 없이 보낸다.
      reportError('game-tracker-result', error);
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

type RecommendationResult =
  { ok: true; ms: number; empty: boolean } | { ok: false; ms: number; kind: ApiErrorKind };

function avgRecommendMs(current: Game) {
  const count = current.recommendSuccess + current.recommendError;
  return count === 0 ? undefined : Math.round(current.recommendMsTotal / count);
}

export function recordRecommendation(result: RecommendationResult) {
  if (!game) return;

  // 실패도 사용자가 기다린 시간이라 같이 잰다 (타임아웃이면 60초).
  game.recommendMsTotal += result.ms;
  game.recommendMaxMs = Math.max(game.recommendMaxMs, result.ms);

  if (!result.ok) {
    game.recommendError++;
    game.recommendErrorByKind[result.kind]++;
    return;
  }

  game.recommendSuccess++;
  if (result.empty) game.recommendEmpty++;
  if (game.firstRecommendSec === undefined) {
    game.firstRecommendSec = Math.round((Date.now() - game.startedAt) / 1000);
  }
}

export type SkipReason = 'noSelf' | 'noPatch' | 'noDDragon';

export function recordSkip(reason: SkipReason) {
  if (game) game.skips[reason]++;
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
export function recordPurchases(
  newItemIds: number[],
  recommendations: RecommendedItem[] | null,
  situationalItems: SituationalItem[] = [],
) {
  if (!game) return;

  for (const itemId of newItemIds) {
    game.purchases++;
    const rank = recommendations?.findIndex((item) => item.id === itemId) ?? -1;
    if (rank >= 0) game.followed++;
    if (rank === 0) game.followedTop1++;
    if (situationalItems.some((item) => item.id === itemId)) game.followedSituational++;
  }
}
