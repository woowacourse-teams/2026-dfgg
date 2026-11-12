import type { ChampSelectMode } from '../../shared/types';
import type { LcuGameflowSession } from './gameflow.types';
import { fetchGameflowSession } from './service';

/** 아이템 추천 오버레이를 띄우는 큐. 소환사의 협곡 일반·랭크 */
export const OVERLAY_QUEUES = new Set([
  400, // 일반 (드래프트)
  420, // 솔로 랭크
  430, // 일반 (블라인드). 지금은 거의 안 열리지만 다시 열릴 때를 위해 둔다
  440, // 자유 랭크
  480, // Swiftplay
  490, // Quickplay
]);

/** 무작위 총력전: 아수라장의 gameMode. 큐 id 는 사용자 설정·시즌마다 달라질 수 있어 모드로 가른다 */
export const MAYHEM_MODE = 'KIWI';

/** 지금 판의 큐. 세션을 못 읽으면 null */
export async function fetchQueue() {
  const session = await fetchGameflowSession().catch(() => null);
  return session?.gameData.queue ?? null;
}

/** 소환사의 협곡 기본 모드. 사용자 설정 게임은 큐 id 가 없어 모드로 가른다 */
const CLASSIC_MODE = 'CLASSIC';

/**
 * 오버레이를 띄울 판인지.
 * 협곡 일반·랭크 큐이거나, 협곡에서 하는 사용자 설정 게임이면 띄운다.
 * 사용자 설정 게임은 큐 정보가 비어 있어서 map.gameMode(없으면 queue.gameMode)로 모드를 본다.
 */
export function isOverlayGame(session: LcuGameflowSession | null): boolean {
  if (!session) return false;
  const { gameData, map } = session;
  if (OVERLAY_QUEUES.has(gameData.queue.id)) return true;
  const mode = map?.gameMode || gameData.queue.gameMode;
  return gameData.isCustomGame === true && mode === CLASSIC_MODE;
}

/** 지금 게임이 오버레이를 띄울 판인지. 세션을 못 읽으면 띄우지 않는다 */
export async function isOverlayQueue(): Promise<boolean> {
  const session = await fetchGameflowSession().catch(() => null);
  return isOverlayGame(session);
}

/** 밴픽 화면을 고를 모드. 세션을 못 읽으면 협곡으로 둔다 */
export async function fetchChampSelectMode(): Promise<ChampSelectMode> {
  const queue = await fetchQueue();
  return queue?.gameMode === MAYHEM_MODE ? 'mayhem' : 'rift';
}
