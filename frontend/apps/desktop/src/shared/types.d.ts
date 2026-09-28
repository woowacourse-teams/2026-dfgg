import type { LcuEventMap } from '../main/lcu/events';

// 피드백 타입
export type FeedbackRating = 'good' | 'soso' | 'bad';

// 게임 끝났을 때 메인이 home에 알려주는 정보. 피드백에 같이 담아 보낸다.
export type EndedGame = {
  riotId?: string;
  gameId?: number;
  championId?: number;
  queue?: string | null;
  result: string;
};

export type PhaseListener = (phase: GameflowPhase | null) => void;
export type StatusListener = (status: LcuStatus | null) => void;

export type Lockfile = {
  name: string;
  pid: string;
  port: string;
  password: string;
  protocol: string;
};

export interface Summoner {
  puuid: string;
  summonerId: number;
  displayName: string;
  gameName: string;
  tagLine: string;
  summonerLevel: number;
  profileIconId: number;

  xpSinceLastLevel: number;
  xpUntilNextLevel: number;
  percentCompleteForNextLevel: number;
}

export type LcuEvent = {
  [K in keyof LcuEventMap]: {
    data: LcuEventMap[K];
    eventType: 'Create' | 'Update' | 'Delete';
    uri: K;
  };
}[keyof LcuEventMap];

/*
| { uri: '/lol-gameflow/v1/gameflow-phase', data: GmaeflowPhase}
*/

export type GameflowPhase =
  | 'None'
  | 'Lobby'
  | 'Matchmaking'
  | 'ReadyCheck'
  | 'ChampSelect'
  | 'GameStart'
  | 'InProgress'
  | 'WaitingForStats'
  | 'PreEndOfGame'
  | 'EndOfGame'
  | 'Reconnect'
  | 'TerminatedInError';

export type RecommendationUpdate = {
  items: RecommendedItem[] | null;
  purchasedCount: number;
};

export type LcuStatus = 'disconnected' | 'connecting' | 'connected';
export type LcuState = {
  status: LcuStatus;
  phase: GameflowPhase | null;
  recommendations: RecommendedItem[] | null;
  purchasedCount?: number;
};

// ---------- 랭크 정보 (/lol-ranked/v1/current-ranked-stats) ----------

/** 랭크가 매겨지는 큐 종류. 실제 응답에서 관측된 키들이다. */
export type RankedQueueType =
  | 'RANKED_SOLO_5x5' // 솔로/듀오 랭크 ← 보통 이것만 쓴다
  | 'RANKED_FLEX_SR' // 자유 랭크 (협곡 5인)
  | 'RANKED_TFT' // 전략적 팀 전투
  | 'RANKED_TFT_DOUBLE_UP' // TFT 더블 업 (2인 1팀)
  | 'RANKED_PREMADE_5x5' // 폐지된 구 5인 팀 랭크. 항상 빈 값
  | 'JADE_RANKED_SOLO_5x5'; // 내부 코드명으로 보이는 큐. 빈 값

/** 언랭이면 빈 문자열로 온다. null 이 아니다. */
export type Tier =
  | ''
  | 'IRON'
  | 'BRONZE'
  | 'SILVER'
  | 'GOLD'
  | 'PLATINUM'
  | 'EMERALD'
  | 'DIAMOND'
  | 'MASTER'
  | 'GRANDMASTER'
  | 'CHALLENGER';

/** I 이 가장 높고 IV 가 가장 낮다. 언랭이면 'NA'. */
export type Division = 'I' | 'II' | 'III' | 'IV' | 'NA';

export type LcuCurrentRankedStats = {
  /** 큐별 랭크 정보. 실무에서는 이것만 쓰면 된다. */
  queueMap: Record<RankedQueueType, RankedEntry>;
  /** queueMap 과 같은 내용을 배열로 담은 것. 중복이라 쓸 일이 없다. */
  queues: RankedEntry[];

  /** 모든 큐 중 티어가 가장 높은 엔트리 (TFT 포함) */
  highestRankedEntry: RankedEntry;
  /** 소환사의 협곡(SR)만 따진 최고 엔트리 */
  highestRankedEntrySR: RankedEntry;

  /** 이번 시즌 협곡에서 찍었던 최고 티어 */
  highestCurrentSeasonReachedTierSR: Tier;
  /** 지난 시즌 마감 시점의 최고 티어. 개별 엔트리 값과 다를 수 있다(큐 전체 기준). */
  highestPreviousSeasonEndTier: Tier;
  highestPreviousSeasonEndDivision: Division;

  /** 시즌 보상 진행도. 화면에 쓸 일은 거의 없다. */
  currentSeasonSplitPoints: number;
  previousSeasonSplitPoints: number;
  /** 프로필 테두리 장식 레벨 */
  rankedRegaliaLevel: number;
  earnedRegaliaRewardIds: string[];
  seasons: unknown;
  splitsProgress: unknown;
};

export type RankedEntry = {
  queueType: RankedQueueType;

  // ---- 지금 랭크. 화면에 실제로 쓰는 값들 ----
  /** 언랭이면 '' */
  tier: Tier;
  division: Division;
  /** LP */
  leaguePoints: number;
  wins: number;
  losses: number;

  // ---- 배치고사 ----
  /** true 면 티어가 없으므로 '배치 3/5' 같은 표시로 바꿔야 한다. */
  isProvisional: boolean;
  provisionalGamesRemaining: number;
  /** 배치 총 판수 (5) */
  provisionalGameThreshold: number;
  /** 승급전 진행. 'WLN' 처럼 판별 결과. 승급전이 아니면 '' */
  miniSeriesProgress: string;

  // ---- 과거 기록 ----
  /** 역대 최고 도달 티어 */
  highestTier: Tier;
  highestDivision: Division;
  previousSeasonEndTier: Tier;
  previousSeasonEndDivision: Division;
  previousSeasonHighestTier: Tier;
  previousSeasonHighestDivision: Division;

  // ---- 보상·기타 ----
  currentSeasonWinsForRewards: number;
  previousSeasonWinsForRewards: number;
  /** 연승 등으로 상승세 표시가 켜졌는지 */
  climbingIndicatorActive: boolean;
  /** 랭크 관련 경고(탈주 등). 없으면 null */
  warnings: unknown;

  // ---- 아레나 전용. 협곡 랭크와 별개 체계라 tier 와 헷갈리면 안 된다 ----
  ratedTier: string; // 'NONE' | 'BRONZE' | ...
  ratedRating: number;
};

// ---------- 전적 (/lol-match-history) ----------

/** 전적 카드 한 장. 원본에서 필요한 것만 추려 IPC 로 보낸다. */
export type MatchSummary = {
  gameId: number;
  queueId: number;
  /** 클라이언트가 주는 한글 이름. 예: '자유 랭크 게임', '무작위 총력전: 아수라장' */
  queueName: string;
  /** 'CLASSIC' | 'ARAM' | 'KIWI' 등. 모드마다 보여줄 지표가 달라 화면을 가를 때 쓴다. */
  gameMode: string;
  /** 사용자 설정 게임. 연습 판이라 승률 집계에서 뺄지 화면이 정한다. */
  isCustom: boolean;
  /** 초. 화면에서 분:초로 바꾼다. */
  gameDuration: number;
  gameCreationDate: string;

  // ---- 내 성적 ----
  /** 내 인게임 슬롯 번호. 펼쳤을 때 10명 중 나를 집어내는 데 쓴다. */
  participantId: number;
  win: boolean;
  championId: number;
  spells: [number, number];
  stats: MatchStats;
};

/** 요약과 상세가 같은 모양으로 쓰는 개인 성적. 화면이 한 벌로 그릴 수 있게 맞춰 둔다. */
export type MatchStats = {
  kills: number;
  deaths: number;
  assists: number;
  /** 게임이 끝났을 때의 챔피언 레벨 */
  champLevel: number;
  /** 미니언 + 정글 몬스터 */
  cs: number;
  goldEarned: number;
  /** 챔피언에게 넣은 피해량. 오브젝트 딜은 빠져 있다. */
  damageDealt: number;
  damageTaken: number;
  visionScore: number;
  wardsPlaced: number;
  wardsKilled: number;
  /** item0~6 을 배열로 편 것. 마지막이 장신구. 빈 칸은 0. */
  items: number[];
};

// ---------- 참여 챔피언 전적 상세 정보 ---------
export type MatchDetail = {
  gameId: number;
  /** 팀별 승패. 상대 팀을 내 승패의 반대로 추측하지 않아도 된다. */
  teams: MatchTeam[];
  participants: MatchParticipant[];
};

export type MatchTeam = {
  /** 100 = 블루, 200 = 레드 */
  teamId: number;
  win: boolean;
};

export type MatchParticipant = {
  participantId: number;
  championId: number;
  gameName: string;
  tagLine: string;
  /** 100 = 블루, 200 = 레드 */
  teamId: number;
  spells: [number, number];
  stats: MatchStats;
};

// ---------- 백엔드 아이템 추천 응답 ----------
export interface NamedEntry {
  id: number;
  name: string;
  imageUrl: string;
}

export interface RecommendedItemDescription {
  counter: NamedEntry[];
  ally: NamedEntry[];
  traits: string[];
}

export interface RecommendedItem extends NamedEntry {
  description: RecommendedItemDescription;
}

export interface RecommendationResponse {
  recommendedItems: RecommendedItem[];
}
