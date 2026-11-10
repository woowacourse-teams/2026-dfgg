import { BrowserWindow } from 'electron';
import type { GameflowPhase, MayhemAugments, MayhemSession } from '../../shared/types';
import { fetchMayhemAugments } from '../api/augments';
import { getLcuState, onPhaseChange } from '../lcu/state';
import { getDDragonData } from '../live/ddragon';
import { getActivePlayer, getGameStats, getPlayerList } from '../live/endpoints';
import { toChampionAlias } from '../live/payload';
import { closeWindow, openWindow, setWindowFactory } from './window';
import { startCaptureTest } from './captureTest';
import { loadCandidates, type Candidate } from './titleImages';

const MAYHEM_MODE = 'KIWI';
const RETRY_MS = 3000;

let gameToken = 0;
let current: MayhemSession | null = null;
let candidatesReady: Promise<Candidate[]> | null = null;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export function getMayhemSession() {
  return current;
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

async function start() {
  const token = ++gameToken;
  const alive = () => token === gameToken && getLcuState().phase === 'InProgress';

  const stats = await retry(alive, getGameStats);
  if (!stats || stats.gameMode !== MAYHEM_MODE) return;

  const championId = await retry(alive, findMyChampionId);
  if (!championId) return;

  let loaded: MayhemAugments;

  try {
    loaded = await fetchMayhemAugments(championId);
  } catch {
    return; // Sentry로 보낸다. 이번 판은 증강 표시 없이 간다.
  }

  if (!alive()) return;
  current = { championId };
  openWindow();
  startCaptureTest(alive);
  candidatesReady = loadCandidates(loaded.augments, loaded.patch).catch((error) => {
    console.error('[mayhem] 제목 견본 실패', error);
    return [];
  });
  console.log('[mayhem] start', championId, loaded.augments.length);
}

function stop() {
  gameToken++;
  current = null;
  candidatesReady = null;
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
