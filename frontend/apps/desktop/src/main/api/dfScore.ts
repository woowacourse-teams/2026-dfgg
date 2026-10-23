import type { DfScores } from '../../shared/types';
import type { LcuGame } from '../lcu/matchHistory.types';
import { apiPost } from './client';

/** 백엔드가 받는 참가자 한 명. 필드 이름은 /api/df-score 문서를 따른다. */
type DfScoreParticipant = {
  puuid: string;
  teamId: number;
  position?: string;
  win: boolean;
  kills: number;
  deaths: number;
  assists: number;
  totalDamageDealtToChampions: number;
  totalDamageTaken: number;
  goldEarned: number;
  visionScore: number;
  damageDealtToObjectives: number;
  damageDealtToTurrets: number;
};

type DfScoreRequest = {
  queueId: number;
  gameDurationSec: number;
  participants: DfScoreParticipant[];
};

const POSITION = ['TOP', 'JUNGLE', 'MID', 'BOTTOM', 'SUPPORT'] as const;

/** LCU 경기 상세를 DF 스코어 요청으로 바꾼다. puuid 를 모르는 자리는 뺀다. */
export function toDfScoreRequest(game: LcuGame): DfScoreRequest {
  const puuids = new Map(
    game.participantIdentities.map((identity) => [identity.participantId, identity.player.puuid]),
  );

  const participants = [...game.participants]
    .sort((a, b) => a.participantId - b.participantId)
    .flatMap((participant, index) => {
      const puuid = puuids.get(participant.participantId);
      if (!puuid) return [];
      const { stats } = participant;
      return {
        puuid,
        teamId: participant.teamId,
        position: POSITION[index % 5] ?? undefined,
        win: stats.win,
        kills: stats.kills,
        deaths: stats.deaths,
        assists: stats.assists,
        totalDamageDealtToChampions: stats.totalDamageDealtToChampions,
        totalDamageTaken: stats.totalDamageTaken,
        goldEarned: stats.goldEarned,
        visionScore: stats.visionScore,
        damageDealtToObjectives: stats.damageDealtToObjectives ?? 0,
        damageDealtToTurrets: stats.damageDealtToTurrets ?? 0,
      };
    });

  return {
    queueId: game.queueId,
    gameDurationSec: game.gameDuration,
    participants: participants,
  };
}

type DfScoreBadge = 'MVP' | 'ACE';

/** /api/df-score 응답. 점수를 매길 수 없는 판·참가자는 score 가 null 로 온다. rank 는 오지 않는다. */
type DFScoreResponse = {
  participants: { puuid: string; score: number | null; badge: DfScoreBadge | null }[];
};

type ScoreEntry = { puuid: string; score: number; rank?: number };

/**
 * 응답에서 참가자별 점수를 꺼낸다.
 * ponytail: 문서에 data 가 {} 로만 적혀 있어 형식을 모른다. data.participants 나 data 가
 * [{ puuid, score, rank? }] 배열이라고 보고, 그 밖의 모양이면 null 을 돌려 화면에서 숨긴다.
 * 형식이 정해지면 이 함수만 고치면 된다.
 */
export function parseDfScores(body: DFScoreResponse | null): ScoreEntry[] | null {
  // 점수가 null 인 사람은 순위에서 빼고 화면에도 띄우지 않는다
  const list = body?.participants?.filter(
    (entry): entry is ScoreEntry & { badge: DfScoreBadge | null } =>
      typeof entry.score === 'number' && Number.isFinite(entry.score),
  );
  if (!list || list.length === 0) return null;

  const ranked = [...list].sort((a, b) => b.score - a.score);
  return list.map((entry) => ({ ...entry, rank: ranked.indexOf(entry) + 1 }));
}

// 같은 판을 여러 번 펼쳐도 한 번만 묻는다. 끝난 판의 점수는 바뀌지 않는다.
const cache = new Map<number, DfScores>();

/** 한 판의 DF 스코어를 참가자 번호별로 돌려준다. 형식을 알아볼 수 없으면 null. */
export async function fetchDfScores(game: LcuGame): Promise<DfScores | null> {
  const cached = cache.get(game.gameId);
  if (cached) return cached;

  const body = await apiPost<DFScoreResponse>('/api/df-score', toDfScoreRequest(game));
  const entries = parseDfScores(body);
  if (!entries) return null;

  const participantIds = new Map(
    game.participantIdentities.map((identity) => [identity.player.puuid, identity.participantId]),
  );
  const scores: DfScores = {};
  for (const { puuid, score, rank } of entries) {
    const participantId = participantIds.get(puuid);
    if (participantId !== undefined && rank !== undefined) scores[participantId] = { score, rank };
  }

  cache.set(game.gameId, scores);
  return scores;
}
