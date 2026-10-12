import type { Lockfile } from '../../shared/types';
import { getGameQueues } from './endpoints';

let cached: Map<number, string> | null = null;

/**
 * 큐 id → 한글 이름표.
 * 큐 목록은 패치 때만 바뀌므로 앱이 살아 있는 동안 한 번만 받아 둔다.
 * 직접 표를 들고 있으면 아수라장 같은 이벤트 큐를 계속 따라가야 해서 클라이언트에 물어본다.
 */
export async function getQueueNames(lockfile: Lockfile) {
  if (cached) return cached;

  const queues = await getGameQueues(lockfile);
  // 실패하면 캐시하지 않는다. 비어 있는 표가 굳으면 영영 '기타'로만 나온다.
  if (!queues) return new Map<number, string>();

  cached = new Map(queues.map((queue) => [queue.id, queue.shortName || queue.name]));
  return cached;
}
