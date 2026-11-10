import type { MayhemAugments } from '../../shared/types';
import { apiGet } from './client';

export type TitleImage = {
  augmentId: number;
  name: string;
  width: number;
  height: number;
  png: string; // base64
};

export type AugmentTitles = {
  patch: string;
  lang: string;
  font: string;
  fontSize: number;
  padding: number;
  images: TitleImage[];
};

export function fetchMayhemAugments(championId: number) {
  return apiGet<MayhemAugments>(`/api/aram-mayhem/champions/${championId}/augments`);
}

export async function fetchAugmentTitles(patch: string, lang: string): Promise<AugmentTitles> {
  const query = new URLSearchParams({ patch, lang });
  return apiGet<AugmentTitles>(`/api/aram-mayhem/augment-titles?${query}`);
}
