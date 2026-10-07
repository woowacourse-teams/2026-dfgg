/**
 * 브라우저에서 home 화면을 바로 열어 볼 때 쓰는 가짜 preload API.
 * Electron 없이(= 롤 클라이언트 없이) 화면을 그리기 위한 것으로, 개발 서버에서만 번들에 들어간다(webpack.config.js).
 *
 * 주소에 값을 붙여 상태를 바꾼다.
 *   /home/index.html                      연결됨 · 로비 (기본)
 *   /home/index.html?status=disconnected  롤 클라이언트 꺼짐
 *   /home/index.html?phase=ChampSelect    챔피언 선택 중
 */
import type {
  GameflowPhase,
  LcuCurrentRankedStats,
  LcuStatus,
  MatchDetail,
  MatchParticipant,
  MatchStats,
  MatchSummary,
  RankedEntry,
  RankedQueueType,
  Summoner,
} from '../../shared/types';

const params = new URLSearchParams(window.location.search);
const status = (params.get('status') ?? 'connected') as LcuStatus;
const phase = (params.get('phase') ?? 'None') as GameflowPhase;

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const BLUE_TEAM = 100;
const RED_TEAM = 200;
const TEAM_SIZE = 5;
const PHASE_DELAY_MS = 300;

const summoner: Summoner = {
  puuid: 'mock-puuid',
  summonerId: 1,
  displayName: 'Hide on bush',
  gameName: 'Hide on bush',
  tagLine: 'KR1',
  summonerLevel: 742,
  profileIconId: 6,
  xpSinceLastLevel: 1840,
  xpUntilNextLevel: 3360,
  percentCompleteForNextLevel: 55,
};

function rankedEntry(queueType: RankedQueueType, patch: Partial<RankedEntry> = {}): RankedEntry {
  return {
    queueType,
    tier: '',
    division: 'NA',
    leaguePoints: 0,
    wins: 0,
    losses: 0,
    isProvisional: false,
    provisionalGamesRemaining: 0,
    provisionalGameThreshold: 5,
    miniSeriesProgress: '',
    highestTier: '',
    highestDivision: 'NA',
    previousSeasonEndTier: '',
    previousSeasonEndDivision: 'NA',
    previousSeasonHighestTier: '',
    previousSeasonHighestDivision: 'NA',
    currentSeasonWinsForRewards: 0,
    previousSeasonWinsForRewards: 0,
    climbingIndicatorActive: false,
    warnings: null,
    ratedTier: 'NONE',
    ratedRating: 0,
    ...patch,
  };
}

const solo = rankedEntry('RANKED_SOLO_5x5', {
  tier: 'EMERALD',
  division: 'II',
  leaguePoints: 47,
  wins: 128,
  losses: 109,
});
const flex = rankedEntry('RANKED_FLEX_SR', {
  tier: 'PLATINUM',
  division: 'I',
  leaguePoints: 82,
  wins: 31,
  losses: 24,
});

const rankInfo: LcuCurrentRankedStats = {
  queueMap: {
    RANKED_SOLO_5x5: solo,
    RANKED_FLEX_SR: flex,
    RANKED_TFT: rankedEntry('RANKED_TFT'),
    RANKED_TFT_DOUBLE_UP: rankedEntry('RANKED_TFT_DOUBLE_UP'),
    RANKED_PREMADE_5x5: rankedEntry('RANKED_PREMADE_5x5'),
    JADE_RANKED_SOLO_5x5: rankedEntry('JADE_RANKED_SOLO_5x5'),
  },
  queues: [solo, flex],
  highestRankedEntry: solo,
  highestRankedEntrySR: solo,
  highestCurrentSeasonReachedTierSR: 'EMERALD',
  highestPreviousSeasonEndTier: 'PLATINUM',
  highestPreviousSeasonEndDivision: 'I',
  currentSeasonSplitPoints: 0,
  previousSeasonSplitPoints: 0,
  rankedRegaliaLevel: 0,
  earnedRegaliaRewardIds: [],
  seasons: null,
  splitsProgress: null,
};

type Build = { championId: number; spells: [number, number]; items: number[] };

// 챔피언 · 주문 · 아이템은 실제 id 라서 아이콘이 그대로 뜬다.
const BUILDS: Build[] = [
  { championId: 103, spells: [4, 14], items: [6655, 3020, 3157, 3089, 3135, 0, 3363] },
  { championId: 64, spells: [4, 11], items: [6692, 3047, 3071, 3156, 0, 0, 3364] },
  { championId: 222, spells: [4, 7], items: [6672, 3006, 3031, 3094, 3036, 0, 3363] },
  { championId: 412, spells: [4, 14], items: [3190, 3009, 3109, 2055, 0, 0, 3364] },
  { championId: 86, spells: [4, 12], items: [3078, 3047, 3742, 3065, 0, 0, 3340] },
  { championId: 157, spells: [4, 14], items: [6672, 3006, 3031, 3072, 0, 0, 3340] },
  { championId: 51, spells: [4, 7], items: [3031, 3006, 3094, 3036, 3072, 0, 3363] },
  { championId: 99, spells: [4, 14], items: [6655, 3020, 3089, 3157, 0, 0, 3363] },
  { championId: 122, spells: [4, 12], items: [3071, 3047, 3053, 3742, 0, 0, 3340] },
  { championId: 89, spells: [4, 3], items: [3190, 3047, 3109, 2055, 0, 0, 3364] },
];

function stats(seed: number, items: number[]): MatchStats {
  return {
    kills: (seed * 7) % 14,
    deaths: (seed * 3) % 9,
    assists: (seed * 5) % 17,
    champLevel: 11 + (seed % 8),
    cs: 120 + ((seed * 37) % 160),
    goldEarned: 8200 + ((seed * 913) % 7400),
    damageDealt: 9000 + ((seed * 2131) % 26000),
    damageTaken: 7000 + ((seed * 1777) % 21000),
    visionScore: 12 + ((seed * 11) % 45),
    wardsPlaced: 6 + (seed % 14),
    wardsKilled: seed % 7,
    items,
  };
}

const QUEUES = [
  { queueId: 420, queueName: '솔로 랭크 게임', gameMode: 'CLASSIC' },
  { queueId: 440, queueName: '자유 랭크 게임', gameMode: 'CLASSIC' },
  { queueId: 450, queueName: '무작위 총력전', gameMode: 'ARAM' },
];
const MATCH_COUNT = 12;
const MY_PARTICIPANT_ID = 1;

const matches: MatchSummary[] = Array.from({ length: MATCH_COUNT }, (_, index) => {
  const build = BUILDS[index % BUILDS.length];
  const queue = QUEUES[index % 4 === 3 ? 2 : index % 2];

  return {
    gameId: 7_000_000_000 + index,
    ...queue,
    isCustom: false,
    gameDuration: (18 + ((index * 7) % 21)) * 60 + ((index * 13) % 60),
    gameCreationDate: new Date(Date.now() - (index * 5 + 1) * HOUR_MS).toISOString(),
    participantId: MY_PARTICIPANT_ID,
    win: index % 3 !== 1,
    championId: build.championId,
    spells: build.spells,
    stats: stats(index + 3, build.items),
  };
});

function matchDetail(gameId: number): MatchDetail | null {
  const match = matches.find((candidate) => candidate.gameId === gameId);
  if (!match) return null;

  const participants: MatchParticipant[] = BUILDS.map((build, index) => {
    const participantId = index + 1;
    const isMe = participantId === match.participantId;

    return {
      participantId,
      championId: isMe ? match.championId : build.championId,
      gameName: isMe ? summoner.gameName : `소환사${participantId}`,
      tagLine: isMe ? summoner.tagLine : 'KR1',
      teamId: index < TEAM_SIZE ? BLUE_TEAM : RED_TEAM,
      spells: isMe ? match.spells : build.spells,
      stats: isMe ? match.stats : stats((gameId % 97) + index, build.items),
    };
  });

  return {
    gameId,
    teams: [
      { teamId: BLUE_TEAM, win: match.win },
      { teamId: RED_TEAM, win: !match.win },
    ],
    participants,
  };
}

const connected = status === 'connected';
const log =
  (name: string) =>
  (...args: unknown[]) =>
    console.debug(`[mock] ${name}`, ...args);
const unsubscribe = () => {};

// Electron 안에서는 진짜 preload 가 이미 있으므로 건드리지 않는다.
if (!window.lcu) {
  window.lcu = {
    currentSummoner: async () => (connected ? summoner : null),
    getRankInfo: async () => (connected ? rankInfo : null),
    getState: async () => ({ status, phase: connected ? 'None' : null, recommendations: null }),
    getMatchHistoryInfo: async () => (connected ? matches : null),
    getMatchDetail: async (gameId) => matchDetail(gameId),
    onStatusChange: () => unsubscribe,
    // 실제 앱처럼 로비에서 시작해 전적을 받은 뒤 phase 가 바뀌게 한다. 처음부터 다른 phase 면 전적을 받지 않는다.
    onPhaseChange: (callback) => {
      const timer =
        connected && phase !== 'None'
          ? setTimeout(() => callback(phase), PHASE_DELAY_MS)
          : undefined;
      return () => clearTimeout(timer);
    },
    onItemsRecommendationChange: () => unsubscribe,
    getEndedGame: async () => null,
  };
  window.windowControls = {
    setCollapsed: log('windowControls.setCollapsed'),
    minimize: log('windowControls.minimize'),
    close: log('windowControls.close'),
  };
  window.analytics = { track: log('analytics.track') };
  window.feedback = { submit: async () => ({}) };
}
