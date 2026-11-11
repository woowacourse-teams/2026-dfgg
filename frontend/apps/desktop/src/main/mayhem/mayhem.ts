import { BrowserWindow } from 'electron';
import type { GameflowPhase, MayhemAugments, MayhemCard } from '../../shared/types';
import { fetchMayhemAugments } from '../api/augments';
import { MAYHEM_MODE } from '../lcu/queue';
import { getLcuState, onPhaseChange } from '../lcu/state';
import { getDDragonData } from '../live/ddragon';
import { getActivePlayer, getGameStats, getPlayerList } from '../live/endpoints';
import { toChampionAlias } from '../live/payload';
import { closeWindow, openWindow, sendToAugmentWindow, setWindowFactory } from './window';
import { loadCandidates } from './titleImages';
import { startCardReader } from './cardReader';
import { judgeTitle } from './cardJudge';
import { beginMayhemStats } from './mayhemStats';
import { trackEvent } from '../analytics/umami';
import { reportError } from '../sentry';

const RETRY_MS = 3000;

let gameToken = 0;
let lastSent = '';
let lastCards: MayhemCard[] = [];

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export function getMayhemCards() {
  return lastCards;
}

async function retry<T>(alive: () => boolean, load: () => Promise<T | null>): Promise<T | null> {
  while (alive()) {
    const value = await load().catch(() => null);
    if (value) return value;
    await sleep(RETRY_MS);
  }
  return null;
}

async function findMyChampionId() {
  const [active, players, { championNames, championKeys }] = await Promise.all([
    getActivePlayer(),
    getPlayerList(),
    getDDragonData(),
  ]);
  const me = active?.riotId ?? active?.summonerName;
  const player = players?.find((p) => p.riotId === me || p.summonerName === me);
  if (!player) return null;
  return championKeys[toChampionAlias(player.rawChampionName, championNames)];
}

function sendCards(cards: MayhemCard[]) {
  const key = JSON.stringify(cards.map((c) => c.augment?.augmentId ?? null));
  if (key === lastSent) return;
  lastSent = key;
  lastCards = cards;
  sendToAugmentWindow('mayhem:cards', cards);
}

async function start() {
  const token = ++gameToken;
  const alive = () => token === gameToken && getLcuState().phase === 'InProgress';

  const stats = await retry(alive, getGameStats);
  if (!stats || stats.gameMode !== MAYHEM_MODE) return;

  // 아수라장 판에서 증강 인식을 켰는지, 못 켰다면 어디서 멈췄는지 남긴다
  const track = (result: string, data?: Record<string, number>) =>
    trackEvent('desktop-mayhem-start', { result, ...data });

  const championId = await retry(alive, findMyChampionId);
  if (!championId) {
    if (alive()) track('no-champion');
    return;
  }

  let loaded: MayhemAugments;

  try {
    loaded = await fetchMayhemAugments(championId);
  } catch {
    // apiGet 이 Sentry 로 보냈다. 이번 판은 증강 표시 없이 간다.
    track('augments-error', { championId });
    return;
  }

  if (!alive()) return;
  const candidates = await loadCandidates(loaded.augments, loaded.patch).catch((error) => {
    console.error('[mayhem] 제목 견본 실패', error);
    reportError('mayhem-title-images', error);
    return [];
  });
  if (!alive()) return;
  if (candidates.length === 0) {
    // 증강은 받았는데 제목 견본과 하나도 짝이 안 맞았다. 패치나 언어가 어긋났을 수 있다
    if (loaded.augments.length > 0) {
      reportError(
        'mayhem-no-candidates',
        new Error(`제목 견본 짝 0/${loaded.augments.length} (patch ${loaded.patch})`),
      );
    }
    track('no-candidates', { championId, augments: loaded.augments.length });
    return;
  }

  openWindow();
  beginMayhemStats(championId, loaded.augments.length, candidates.length);
  startCardReader(alive, (mask) => judgeTitle(mask, candidates), sendCards);
  track('ok', { championId, augments: loaded.augments.length, candidates: candidates.length });
  console.log('[mayhem] start', championId, loaded.augments.length);
}

function stop() {
  gameToken++;
  lastSent = '';
  lastCards = [];
  closeWindow();
}

function syncWithPhase(phase: GameflowPhase | null) {
  if (phase === 'InProgress') void start();
  else stop();
}

export function initMayhem(create: () => BrowserWindow) {
  setWindowFactory(create);
  syncWithPhase(getLcuState().phase);
  onPhaseChange(syncWithPhase);
}
