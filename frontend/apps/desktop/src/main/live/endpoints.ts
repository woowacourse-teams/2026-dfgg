import { liveRequest } from './client';
import type { ActivePlayer, LivePlayer } from './types';

// 인게임 정보 얻기
export function getPlayerList() {
  return liveRequest<LivePlayer[]>('/liveclientdata/playerlist');
}

// 내가 누군지 확인하기
export function getActivePlayer() {
  return liveRequest<ActivePlayer>('/liveclientdata/activeplayer');
}
