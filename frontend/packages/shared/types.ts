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

/** direction 코드를 UI에 보여줄 한글 라벨로 바꾼다. API가 한글 설명을 내려주지 않는다. */
export const DIRECTION_LABEL: Record<Direction, string> = {
  PHYSICAL_DAMAGE: '물리 피해 대응',
  MAGIC_DAMAGE: '마법 피해 대응',
  MIXED_DAMAGE: '복합 피해 대응',
  ANTI_TANK: '탱커 대응',
  BURST_SURVIVAL: '순간 피해 생존',
  SUSTAINED_COMBAT: '지속 전투',
  BURST_DAMAGE: '순간 피해',
  SUSTAINED_DAMAGE: '지속 피해',
  SURVIVAL_RESPONSE: '생존 대응',
  BURST_ASSASSINATION: '순간 암살',
  DEFENSE_NEUTRALIZATION: '방어 무력화',
  ENGAGE_SURVIVAL: '진입 후 생존',
  CRITICAL_STRIKE_DAMAGE: '치명타 피해',
  ANTI_TANK_SUSTAINED_DAMAGE: '대탱커 지속 피해',
  SURVIVAL_KITING: '생존 및 카이팅',
  ENGAGE_INITIATION: '전투 개시',
  ALLY_PROTECTION: '아군 보호',
  HEALING_ENHANCEMENT: '회복 및 강화',
};
