import { MayhemAugments } from '../../shared/types';
import { apiGet } from './client';

export function fetchMayhemAugments(championId: number) {
  return apiGet<MayhemAugments>(`/api/aram-mayhem/champions/${championId}/augments`);
}
