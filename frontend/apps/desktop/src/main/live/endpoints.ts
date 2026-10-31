import { liveRequest } from './client';
import type { ActivePlayer, GameStats, LivePlayer } from './types';

// 인게임 정보 얻기
export function getPlayerList() {
  return liveRequest<LivePlayer[]>('/liveclientdata/playerlist');
}

// 게임 경과 시간 등 판 정보 얻기
export function getGameStats() {
  return liveRequest<GameStats>('/liveclientdata/gamestats');
}

// 내가 누군지 확인하기
export function getActivePlayer() {
  return liveRequest<ActivePlayer>('/liveclientdata/activeplayer');
}
