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

export interface RecommendationBuild {
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

/**
 * POST /api/recommendations/v3 요청 본문. 1번(v2)과 달리 지금까지 고른
 * 아이템을 담아 보내고, 하나 고를 때마다 다시 호출해서 다음 후보를 받는다.
 */
export interface RecommendationV3Request {
  myChampion: Champion;
  purchasedItemIds: number[];
  allies: Champion[];
  enemies: Champion[];
  tier: string;
  patch: string;
}

/** v3 응답. builds처럼 세트로 나뉘지 않고, "이 중 하나를 골라라"는 후보 목록을 준다. */
export interface RecommendationV3Response {
  recommendedItems: RecommendationBuild[];
  /** 어떤 방식으로 추천했는지 백엔드가 알려주는 값. 그대로 화면에 표시한다. */
  servedBy: string;
}
