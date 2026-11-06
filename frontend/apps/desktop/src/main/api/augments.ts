import type { MayhemAugments } from '../../shared/types';
import { apiGet } from './client';

/** 챔피언의 증강 픽률 전체. language 는 증강 이름 언어('ko' | 'en') */
export function fetchMayhemAugments(championId: number, language: string) {
  return apiGet<MayhemAugments>(`/api/aram-mayhem/champions/${championId}/augments`, language);
}
