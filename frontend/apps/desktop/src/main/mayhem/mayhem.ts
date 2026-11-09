import { BrowserWindow } from 'electron';
import type { GameflowPhase, MayhemAugments, MayhemSession } from '../../shared/types';
import { fetchMayhemAugments } from '../api/augments';
import { getLcuState, onPhaseChange } from '../lcu/state';
import { getDDragonData } from '../live/ddragon';
import { getActivePlayer, getGameStats, getPlayerList } from '../live/endpoints';
import { toChampionAlias } from '../live/payload';
import { matchIcon, prepareIcons } from './iconMatch';
import { closeWindow, openWindow, setWindowFactory } from './window';
import { registerIpcHandlers } from '../ipc';

const MAYHEM_MODE = 'KIWI';
const RETRY_MS = 3000;

let gameToken = 0;
let augments: MayhemAugments[] = [];
let iconsReady: Promise<number> | null = null;
let current: MayhemSession | null = null;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export function getMayhemSession() {
  return current;
}

export async function readCard(iconPng: Buffer): Promise<MayhemAugments | null> {
  if (!current) return null;
  await iconsReady; // 백엔드 아이콘 리스트를 받아서 캐시에 넣을 때까지 기다리기
  // 증강 카드 한 장이 어떤 증강인지 결과
  const result = await matchIcon(iconPng, augments);
  console.log(
    '[mayhem] card',
    result.kind,
    result.top.map(({ candidate, score }) => `${candidate.name} ${score.toFixed(3)}`),
  );
  return result.picked;
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

  let loaded: MayhemAugments[];

  try {
    loaded = (await fetchMayhemAugments(championId)).augments;
  } catch {
    return; // Sentry로 보낸다. 이번 판은 증강 표시 없이 간다.
  }

  if (!alive()) return;
  augments = loaded;

  iconsReady = prepareIcons(augments);
  const icons = await iconsReady;
  if (!alive()) return;
  current = { championId };
  openWindow();
  console.log('[mayhem] start', championId, augments.length, 'icons', icons);
}

function stop() {
  gameToken++;
  augments = [];
  iconsReady = null;
  current = null;
  closeWindow();
}

function syncWithPhase(phase: GameflowPhase | null) {
  if (phase === 'InProgress') void start();
  else stop();
}

export function initMayhem(create: () => BrowserWindow) {
  setWindowFactory(create);
  registerIpcHandlers();
  syncWithPhase(getLcuState().phase);
  onPhaseChange(syncWithPhase);
}
