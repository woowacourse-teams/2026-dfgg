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

export const CHAMPION_TAGS = [
  'TANK',
  'FIGHTER',
  'MAGE',
  'ASSASSIN',
  'MARKSMAN',
  'SUPPORT',
] as const;

export type ChampionTag = (typeof CHAMPION_TAGS)[number];

/** championTag별로 가질 수 있는 값이 다르다. 문자열 유니온으로만 제약한다. */
export type Direction =
  | 'PHYSICAL_DAMAGE'
  | 'MAGIC_DAMAGE'
  | 'MIXED_DAMAGE'
  | 'ANTI_TANK'
  | 'BURST_SURVIVAL'
  | 'SUSTAINED_COMBAT'
  | 'BURST_DAMAGE'
  | 'SUSTAINED_DAMAGE'
  | 'SURVIVAL_RESPONSE'
  | 'BURST_ASSASSINATION'
  | 'DEFENSE_NEUTRALIZATION'
  | 'ENGAGE_SURVIVAL'
  | 'CRITICAL_STRIKE_DAMAGE'
  | 'ANTI_TANK_SUSTAINED_DAMAGE'
  | 'SURVIVAL_KITING'
  | 'ENGAGE_INITIATION'
  | 'ALLY_PROTECTION'
  | 'HEALING_ENHANCEMENT';

export interface Build {
  championTag: ChampionTag;
  direction: Direction;
  build: Item[];
}

export interface RecommendationResponse {
  champion: string;
  position: Position;
  /** 추천 우선순위 순. builds[0]이 가장 적합하다. 빌드가 없으면 빈 배열. 최대 3개. */
  builds: Build[];
}

/**
 * POST /api/recommendations/v3 요청 본문. v2(builds)와 달리 구매한 아이템
 * 목록을 실어 보내고, 살 때마다 다시 호출해서 다음 추천을 받는 방식이다.
 */
export interface RecommendationV3Request {
  myChampion: Champion;
  purchasedItemIds: number[];
  allies: Champion[];
  enemies: Champion[];
  tier: string;
  patch: string;
}

/** v3 응답. builds처럼 여러 세트로 나뉘지 않고 다음에 살 아이템을 한 줄로 준다. */
export interface RecommendationV3Response {
  recommendedItems: Item[];
  /** 어떤 방식으로 추천했는지 백엔드가 알려주는 값. 그대로 화면에 표시한다. */
  servedBy: string;
}
