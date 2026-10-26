import type { Counters, Position, RuneRecommendation, TodayPicks } from '../../shared/types';
import { apiPost } from './client';

// 실패하면 apiPost 가 Sentry 로 보낸다. 밴픽 화면은 그 칸만 비우고 넘어간다.

export function fetchTodayPicks(position: Position, bans: number[]) {
  return apiPost<TodayPicks>('/api/today-picks', { position, bans });
}

export function fetchCounters(
  myPosition: Position,
  enemies: number[],
  allies: number[],
  bans: number[],
) {
  return apiPost<Counters>('/api/counters', { myPosition, enemies, allies, bans });
}

/** name 은 챔피언 영문 이름(Jayce) */
export function fetchRuneRecommendation(name: string, position: Position) {
  return apiPost<RuneRecommendation>('/api/recommendations/runes', {
    myChampion: { name, position },
  });
}
