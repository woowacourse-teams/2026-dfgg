import type { ChampSelectState, GameflowPhase, Lockfile } from '../../shared/types';
import { fetchCounters, fetchRuneRecommendation, fetchTodayPicks } from '../api/champSelect';
import { broadcastToAllWindows } from '../ipc/broadcast';
import type { LcuChampionSummary, LcuChampSelectSession } from '../lcu/champSelect.types';
import { lcuRequest } from '../lcu/client';
import { getLockfileContent } from '../lcu/lockfile';
import { getLcuState, onPhaseChange } from '../lcu/state';
import { reportError } from '../sentry';
import { applyPerkPage, fromPro, fromRecommended, type PerkSelection } from './perks';
import { type ChampSelectInfo, sameIds, toChampSelectInfo } from './session';

const SESSION_PATH = '/lol-champ-select/v1/session';

let state: ChampSelectState | null = null;
let info: ChampSelectInfo | null = null;
/** 밴픽마다 올린다. 이전 밴픽의 늦은 응답이 새 화면을 덮지 않게 한다. */
let round = 0;

const emptyState = (): ChampSelectState => ({
  position: null,
  myChampionId: null,
  locked: false,
  todayPicks: null,
  counters: null,
  runes: null,
  applied: null,
  runeError: null,
});

function update(patch: Partial<ChampSelectState>) {
  state = { ...(state ?? emptyState()), ...patch };
  broadcastToAllWindows('champ-select:state', state);
}

export const getChampSelectState = (): ChampSelectState | null => state;

/** 요청을 보낸 뒤 밴픽이 끝났거나 조건이 바뀌었으면 응답을 버린다. */
async function load<T>(
  label: string,
  request: () => Promise<T>,
  isCurrent: () => boolean,
  apply: (result: T) => void,
) {
  const startedIn = round;
  try {
    const result = await request();
    if (startedIn === round && isCurrent()) apply(result);
  } catch (error) {
    // apiPost 가 Sentry 로 보냈다. 그 칸만 비워 둔다.
    console.error(`${label} 실패`, error);
  }
}

// 챔피언 id → 영문 이름. 패치 때만 바뀌어 앱이 켜져 있는 동안 한 번만 받는다.
let championNames: Map<number, string> | null = null;

async function championName(lockfile: Lockfile, championId: number) {
  if (!championNames) {
    const list = await lcuRequest<LcuChampionSummary[]>(
      lockfile,
      '/lol-game-data/assets/v1/champion-summary.json',
    );
    championNames = new Map((list ?? []).map(({ id, alias }) => [id, alias]));
  }
  return championNames.get(championId) ?? null;
}

async function applyRunes(selection: PerkSelection | null, applied: ChampSelectState['applied']) {
  const lockfile = getLockfileContent();
  if (!lockfile || !selection) return update({ runeError: 'failed' });

  const error = await applyPerkPage(lockfile, selection);
  update(error ? { runeError: error } : { applied, runeError: null });
}

function loadRunes(next: ChampSelectInfo) {
  const { myChampionId, position } = next;
  const lockfile = getLockfileContent();
  if (!myChampionId || !position || !lockfile) return;

  update({ runes: null, applied: null, runeError: null });
  void load(
    '룬 추천',
    async () => {
      const name = await championName(lockfile, myChampionId);
      if (!name) throw new Error(`챔피언 영문 이름 없음: ${myChampionId}`);
      return fetchRuneRecommendation(name, position);
    },
    () => info?.myChampionId === myChampionId,
    (runes) => {
      update({ runes });
      // 챔피언을 고르면 1순위를 바로 넣는다
      const top = runes.pages[0];
      if (top) void applyRunes(fromRecommended(top), top.rank);
    },
  );
}

function onSession(session: LcuChampSelectSession) {
  const prev = info;
  const next = toChampSelectInfo(session);
  info = next;
  update({ position: next.position, myChampionId: next.myChampionId, locked: next.locked });

  const { position } = next;
  if (!position) return;
  const positionChanged = prev?.position !== position;
  const bansChanged = !sameIds(prev?.bans, next.bans);

  // ponytail: 밴이 바뀔 때마다 다시 받아 무작위 다섯이 섞인다. 밴은 픽 전에 끝나서 그대로 둔다. 거슬리면 밴에 걸린 픽만 뺀다.
  if (positionChanged || bansChanged) {
    void load(
      '오늘의 픽',
      () => fetchTodayPicks(position, next.bans),
      () => sameIds(info?.bans, next.bans),
      (todayPicks) => update({ todayPicks }),
    );
  }

  const picksChanged = !sameIds(prev?.enemies, next.enemies) || !sameIds(prev?.allies, next.allies);
  if (positionChanged || bansChanged || picksChanged) {
    void load(
      '카운터',
      () => fetchCounters(position, next.enemies, next.allies, next.bans),
      () => sameIds(info?.enemies, next.enemies) && sameIds(info?.allies, next.allies),
      (counters) => update({ counters }),
    );
  }

  if (positionChanged || prev?.myChampionId !== next.myChampionId) loadRunes(next);
}

/** 소켓으로 온 세션. 밴픽이 끝나면 null 이 온다. */
export function handleChampSelectSession(session: LcuChampSelectSession | null) {
  if (session && getLcuState().phase === 'ChampSelect') onSession(session);
}

function reset() {
  round += 1;
  info = null;
  if (state === null) return;
  state = null;
  broadcastToAllWindows('champ-select:state', null);
}

async function syncWithPhase(phase: GameflowPhase | null) {
  if (phase !== 'ChampSelect') return reset();

  // 소켓은 바뀔 때만 알려 준다. 밴픽 도중에 앱을 켰을 때를 위해 한 번 직접 받는다.
  const lockfile = getLockfileContent();
  if (!lockfile) return;
  try {
    const session = await lcuRequest<LcuChampSelectSession>(lockfile, SESSION_PATH);
    if (session) handleChampSelectSession(session);
  } catch (error) {
    console.error('밴픽 세션 요청 실패', error);
    reportError('champ-select-session', error);
  }
}

export function initChampSelect() {
  void syncWithPhase(getLcuState().phase);
  onPhaseChange((phase) => void syncWithPhase(phase));
}

/** 탭을 바꾸면 그 추천 페이지를 넣는다. */
export function applyRecommendedPage(rank: number) {
  const page = state?.runes?.pages.find((candidate) => candidate.rank === rank);
  if (!page) return;
  void applyRunes(fromRecommended(page), rank);
}

/** '이 룬 쓰기': 오늘의 픽 선수 룬을 그대로 넣는다. */
export function applyProPage(playerName: string) {
  const pick = state?.todayPicks?.picks.find((candidate) => candidate.player.name === playerName);
  if (!pick) return;
  void applyRunes(fromPro(pick), `pro:${playerName}`);
}
