export const POSITIONS = ['TOP', 'JUNGLE', 'MID', 'BOTTOM', 'SUPPORT'] as const;

export type Position = (typeof POSITIONS)[number];

export interface Champion {
  name: string;
  position: Position;
}

export interface Item {
  id: number;
  name: string;
  imageUrl: string;
}

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
