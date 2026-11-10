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

/** 지금 게임이 오버레이를 띄울 큐인지. 세션을 못 읽으면 띄우지 않는다 */
export async function isOverlayQueue(): Promise<boolean> {
  const session = await fetchGameflowSession().catch(() => null);
  const queueId = session?.gameData.queue.id;
  return queueId !== undefined && OVERLAY_QUEUES.has(queueId);
}
