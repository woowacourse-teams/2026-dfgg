import type { MatchDetail, MatchStats, MatchSummary } from '../../shared/types';
import type {
  LcuGame,
  LcuMatchHistory,
  LcuParticipant,
  LcuParticipantStats,
} from './matchHistory.types';

/** item0~item6 을 배열로 편다. 마지막이 장신구고, 안 산 칸은 0 이라 화면에서 걸러 쓴다. */
function toItems(stats: LcuParticipantStats): number[] {
  return [
    stats.item0,
    stats.item1,
    stats.item2,
    stats.item3,
    stats.item4,
    stats.item5,
    stats.item6,
  ];
}

/** 118개 필드 중 화면에 쓰는 것만 골라 평평하게 편다. */
function toStats(stats: LcuParticipantStats): MatchStats {
  return {
    kills: stats.kills,
    deaths: stats.deaths,
    assists: stats.assists,
    champLevel: stats.champLevel,
    cs: stats.totalMinionsKilled + stats.neutralMinionsKilled,
    goldEarned: stats.goldEarned,
    damageDealt: stats.totalDamageDealtToChampions,
    damageTaken: stats.totalDamageTaken,
    visionScore: stats.visionScore,
    wardsPlaced: stats.wardsPlaced,
    wardsKilled: stats.wardsKilled,
    items: toItems(stats),
  };
}

const toSpells = (participant: LcuParticipant): [number, number] => [
  participant.spell1Id,
  participant.spell2Id,
];

/**
 * 원본 1경기를 화면용 1경기로 접는다.
 * 원본 모양을 아는 곳은 여기뿐이고, 나머지 코드는 MatchSummary 만 본다.
 */
function toMatchSummary(game: LcuGame, queueNames: Map<number, string>): MatchSummary | null {
  // 목록 응답의 participants 에는 나 하나만 들어 있다. 조인할 상대가 없다.
  const me = game.participants[0];
  if (!me) return null;

  return {
    gameId: game.gameId,
    queueId: game.queueId,
    queueName: queueNames.get(game.queueId) ?? '기타',
    gameMode: game.gameMode,
    isCustom: game.gameType === 'CUSTOM_GAME',
    gameDuration: game.gameDuration,
    gameCreationDate: game.gameCreationDate,

    participantId: me.participantId,
    win: me.stats.win,
    championId: me.championId,
    spells: toSpells(me),
    stats: toStats(me.stats),
  };
}

/** 응답 전체를 전적 카드 배열로 접는다. 접지 못한 경기는 뺀다. */
export function toMatchSummaries(
  history: LcuMatchHistory,
  queueNames: Map<number, string>,
): MatchSummary[] {
  return history.games.games
    .map((game) => toMatchSummary(game, queueNames))
    .filter((match): match is MatchSummary => match !== null);
}

/**
 * 카드를 펼쳤을 때 쓰는 10명 기록.
 *
 * participants 와 participantIdentities 는 participantId 로 묶는다.
 * 상세 응답에서는 둘 다 1~10 순서로 오는 것을 확인했지만 보장된 바가 없고,
 * 어긋나면 챔피언과 소환사 이름이 뒤바뀌어 눈으로 잡기 어려운 버그가 된다.
 */
export function toMatchDetail(game: LcuGame): MatchDetail {
  const players = new Map(
    game.participantIdentities.map((identity) => [identity.participantId, identity.player]),
  );

  return {
    gameId: game.gameId,
    // teams 쪽 win 은 'Win' | 'Fail' 문자열이라 불린으로 맞춰 준다.
    teams: game.teams.map((team) => ({ teamId: team.teamId, win: team.win === 'Win' })),
    participants: game.participants.map((participant) => {
      const { participantId, championId, teamId } = participant;
      // 신원이 빠진 자리가 있어도 나머지 9명까지 날리지는 않는다.
      const player = players.get(participantId);

      return {
        participantId,
        championId,
        teamId,
        gameName: player?.gameName ?? '',
        tagLine: player?.tagLine ?? '',
        spells: toSpells(participant),
        stats: toStats(participant.stats),
      };
    }),
  };
}
