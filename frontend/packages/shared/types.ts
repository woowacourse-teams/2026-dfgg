export const POSITIONS = ['TOP', 'JUNGLE', 'MID', 'BOTTOM', 'SUPPORT'] as const;

export type Position = (typeof POSITIONS)[number];

/** 백엔드 ChampionDto. name은 Riot 영문 키다. (예: XinZhao) */
export interface Champion {
  name: string;
  position: Position;
}

/** 백엔드 ItemDto. imageUrl은 내려오지 않으므로 id로 ddragon URL을 만든다. */
export interface Item {
  id: number;
  name: string;
}

/**
 * POST /recommendations 요청 본문.
 * allies는 정확히 4명, enemies는 정확히 5명이어야 서버 검증을 통과한다.
 */
export interface RecommendationRequest {
  myChampion: Champion;
  allies: Champion[];
  enemies: Champion[];
}

interface RecommendationBuild {
  id: number;
  name: string;
}

export interface RecommendationResponse {
  champion: string;
  position: Position;
  builds: {
    championTag: string;
    direction: string;
    build: RecommendationBuild[];
  }[];
}
