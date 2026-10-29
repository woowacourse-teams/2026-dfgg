import type {
  LcuGame,
  LcuMatchHistory,
  LcuParticipant,
  LcuParticipantIdentity,
  LcuParticipantStats,
} from '../../src/main/lcu/matchHistory.types';

/** 한 사람의 경기 기록. 숫자는 모두 0 에서 시작하고 필요한 칸만 바꾼다 */
export function lcuStats(overrides: Partial<LcuParticipantStats> = {}): LcuParticipantStats {
  return {
    win: true,
    kills: 0,
    deaths: 0,
    assists: 0,
    champLevel: 1,
    totalMinionsKilled: 0,
    neutralMinionsKilled: 0,
    goldEarned: 0,
    totalDamageDealtToChampions: 0,
    totalDamageTaken: 0,
    visionScore: 0,
    wardsPlaced: 0,
    wardsKilled: 0,
    item0: 0,
    item1: 0,
    item2: 0,
    item3: 0,
    item4: 0,
    item5: 0,
    item6: 0,
    ...overrides,
  };
}

/** 참가자 번호(1~10)로 한 명을 만든다. 1~5 는 블루(100), 6~10 은 레드(200) */
export function lcuParticipant(
  participantId: number,
  overrides: Partial<LcuParticipant> = {},
): LcuParticipant {
  const teamId = participantId <= 5 ? 100 : 200;
  return {
    participantId,
    teamId,
    championId: participantId,
    spell1Id: 4,
    spell2Id: 14,
    stats: lcuStats({ win: teamId === 100 }),
    ...overrides,
  };
}

export function lcuIdentity(participantId: number): LcuParticipantIdentity {
  return {
    participantId,
    player: { gameName: `player${participantId}`, tagLine: 'KR1', puuid: `puuid-${participantId}` },
  };
}

const PARTICIPANT_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

/** 경기 상세(/games/{id}). 10명이 다 들어 있다. 기본값은 솔로 랭크 30분, 블루 승리 */
export function lcuGame(overrides: Partial<LcuGame> = {}): LcuGame {
  return {
    gameId: 1001,
    queueId: 420,
    gameMode: 'CLASSIC',
    gameType: 'MATCHED_GAME',
    gameDuration: 1800,
    gameCreationDate: '2026-10-28T12:00:00.000Z',
    participants: PARTICIPANT_IDS.map((id) => lcuParticipant(id)),
    participantIdentities: PARTICIPANT_IDS.map(lcuIdentity),
    teams: [
      { teamId: 100, win: 'Win' },
      { teamId: 200, win: 'Fail' },
    ],
    ...overrides,
  };
}

/** 최근 전적 목록. 목록 응답에는 경기마다 '나' 한 명만 들어 있다 */
export function lcuMatchHistory(games: LcuGame[]): LcuMatchHistory {
  return { games: { games } };
}
