import type { LcuEventMap } from '../main/lcu/events';

export type OverlayClickTarget = 'item' | 'situational' | 'collapse' | 'expand' | 'empty' | 'other';

// 전적 카드에 하이라이트 보기 버튼을 그릴 때 필요
export interface HighlightSummary {
  id: string; // 세션 폴더 이름
  count: number; // 클립 수
  duration: number; // 클립 길이 합(초)
}

// 클립 하나 (clips.json 한 항목)
export interface HighlightClip {
  file: string; // *.mp4
  start: number; // 원본 영상 기준 초
  end: number;
  events: number[]; // 포함된 EventID
}

// 한 판의 하이라이트
export interface HighlightGame {
  id: string; // 세션 폴더 이름 (2026-10-10T17-31-05-760Z)
  gameId: number | null;
  startAt: number; // 녹화 시작 시각(ms) 화면에서 "오늘 02:31" 같은 표시에 사용
  clips: HighlightClip[];
}

// 만드는 중 진행 상황
export interface HighlightProgress {
  id: string;
  done: number; // 완성된 클립 수
  total: number; // 만들 클립 수
  clip?: HighlightClip; // 방금 완성된 클립
  finished: boolean; // 전부 끝났는지
}

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

/** 소환사 프로필 꾸밈 정보 (/lol-summoner/v1/current-summoner/summoner-profile). 프로필 배경만 쓴다. */
export type LcuSummonerProfile = {
  /** 챔피언 id * 1000 + 스킨 번호. 예: 103015 = 아리(103)의 15번 스킨 */
  backgroundSkinId?: number;
};

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
  /** 상대 조합 때문에 고려할 아이템. 최대 2개, 없으면 빈 배열 */
  situationalItems: SituationalItem[];
  purchasedCount: number;
  /** 추천을 받을 때의 보유 골드. 모르면 null */
  availableGold: number | null;
};

export type LcuStatus = 'disconnected' | 'connecting' | 'connected';
export type LcuState = {
  status: LcuStatus;
  phase: GameflowPhase | null;
  recommendations: RecommendedItem[] | null;
  situationalItems?: SituationalItem[];
  purchasedCount?: number;
  availableGold?: number | null;
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

/** 코어 하나를 향해 지금 살 하위 아이템 묶음 (추천 v4) */
export interface PurchaseBundle {
  items: { itemId: number; name: string; imageUrl: string; quantity: number }[];
  /** 보유 재료로 조합되는 만큼 뺀 실제 지불 골드. 항상 보유 골드 이하 */
  additionalCost: number;
  /** 실제로 살 수 있는 순서 (선호 순위가 아님) */
  purchaseOrder: number[];
}

export interface RecommendedItem extends NamedEntry {
  description: RecommendedItemDescription;
  /** 살 수 있는 묶음이 없으면 null. v3 응답에는 없다. */
  purchaseBundle?: PurchaseBundle | null;
}

/** 상대 조합 때문에 고려할 아이템 (추천 v4). description.ally 는 항상 빈 배열이다. */
export interface SituationalItem extends NamedEntry {
  description: RecommendedItemDescription;
  additionalCost: number;
  affordable: boolean;
}

export interface RecommendationResponse {
  recommendedItems: RecommendedItem[];
  situationalItems?: SituationalItem[];
}

/** 한 판에서 한 사람의 DF 스코어와 10명 중 순위 */
export type DfScore = { score: number; rank: number };

/** 참가자 번호(participantId) → DF 스코어 */
export type DfScores = Record<number, DfScore>;

/* ---------- 챔피언 선택(밴픽) ---------- */

/** 백엔드가 쓰는 포지션 이름 */
export type Position = 'TOP' | 'JUNGLE' | 'MID' | 'BOTTOM' | 'SUPPORT';

/** 이름과 그림이 붙은 항목. 카탈로그에 아직 없으면 name·imageUrl 이 null 로 온다. */
export interface RuneEntry {
  id: number;
  name: string | null;
  imageUrl: string | null;
}

/** POST /api/recommendations/runes 의 페이지 하나 */
export interface RecommendedRunePage {
  rank: number;
  primaryStyle: RuneEntry;
  keystone: RuneEntry;
  primaryRunes: RuneEntry[];
  subStyle: RuneEntry;
  subRunes: RuneEntry[];
  statShards: RuneEntry[];
  evidence: { games: number; pickRate: number };
}

export interface RuneRecommendation {
  patch: string | null;
  championGames: number;
  /** 판 수가 100판보다 적으면 비어 있다 */
  pages: RecommendedRunePage[];
}

/** POST /api/today-picks 의 프로 픽 하나 */
export interface TodayPick {
  player: { name: string; team: string | null; imageUrl: string | null };
  playedAt: string;
  champion: { id: number; name: string; imageUrl: string; position: string };
  spells: { id: number; name: string; imageUrl: string }[];
  items: { id: number; name: string; imageUrl: string }[];
  runes: {
    primaryTree: { id: number; name: string } | null;
    keystone: RuneEntry | null;
    primary: RuneEntry[];
    secondaryTree: { id: number; name: string } | null;
    secondary: RuneEntry[];
    shards: RuneEntry[];
  };
}

export interface TodayPicks {
  tournament: { name: string; league: string; startDate: string | null } | null;
  position: Position;
  picks: TodayPick[];
}

/** POST /api/counters 의 상대 한 명 */
export interface CounterEnemy {
  championId: number;
  name: string;
  /** 이번 패치 라인 분포. 비율 내림차순, 기록이 없으면 비어 있다 */
  positions: { position: Position; rate: number }[];
  counters: { championId: number; name: string }[];
}

export interface Counters {
  patch: string | null;
  myPosition: Position;
  enemies: CounterEnemy[];
}

/** 룬을 클라이언트에 넣지 못한 이유. no-slot 은 빈 룬 페이지 칸이 없을 때 */
export type RuneApplyError = 'no-slot' | 'failed';

/** 밴픽 화면에 그릴 것 전부. 밴픽이 아니면 null 로 보낸다. */
export interface ChampSelectState {
  /** 추천에 쓰는 포지션. 배정이 없으면 사용자가 고른 포지션, 그것도 없으면 null */
  position: Position | null;
  /** 클라이언트가 포지션을 배정했는지. 블라인드·칼바람·사용자 설정은 false 라 화면에서 고르게 한다 */
  positionAssigned: boolean;
  myChampionId: number | null;
  /** 내 픽을 확정했는지 */
  locked: boolean;
  todayPicks: TodayPicks | null;
  counters: Counters | null;
  runes: RuneRecommendation | null;
  /** 지금 클라이언트에 들어간 페이지. 추천 순위(rank)면 숫자, 프로 룬이면 'pro:선수이름' */
  applied: number | `pro:${string}` | null;
  runeError: RuneApplyError | null;
}
