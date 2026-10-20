import type { DfScores } from '../../shared/types';
import type { LcuGame, LcuParticipant } from '../lcu/matchHistory.types';
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

// LCU 의 lane·role 을 백엔드 포지션 이름으로 바꾼다. 봇 라인은 role 로 원딜과 서폿을 가른다.
const LANE_TO_POSITION: Record<string, string> = {
  TOP: 'TOP',
  JUNGLE: 'JUNGLE',
  MIDDLE: 'MID',
  MID: 'MID',
};

function toPosition(participant: LcuParticipant): string | undefined {
  const lane = participant.timeline?.lane ?? '';
  if (lane === 'BOTTOM' || lane === 'BOT') {
    return participant.timeline?.role === 'DUO_SUPPORT' ? 'SUPPORT' : 'BOTTOM';
  }
  return LANE_TO_POSITION[lane];
}

/** LCU 경기 상세를 DF 스코어 요청으로 바꾼다. puuid 를 모르는 자리는 뺀다. */
export function toDfScoreRequest(game: LcuGame): DfScoreRequest {
  const puuids = new Map(
    game.participantIdentities.map((identity) => [identity.participantId, identity.player.puuid]),
  );

  return {
    queueId: game.queueId,
    gameDurationSec: game.gameDuration,
    participants: game.participants.flatMap((participant) => {
      const puuid = puuids.get(participant.participantId);
      if (!puuid) return [];
      const { stats } = participant;
      return [
        {
          puuid,
          teamId: participant.teamId,
          position: toPosition(participant),
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
        },
      ];
    }),
  };
}

type ScoreEntry = { puuid: string; score: number; rank?: number };

const isScoreEntry = (value: unknown): value is ScoreEntry =>
  typeof value === 'object' &&
  value !== null &&
  typeof (value as ScoreEntry).puuid === 'string' &&
  typeof (value as ScoreEntry).score === 'number' &&
  Number.isFinite((value as ScoreEntry).score);

/**
 * 응답에서 참가자별 점수를 꺼낸다.
 * ponytail: 문서에 data 가 {} 로만 적혀 있어 형식을 모른다. data.participants 나 data 가
 * [{ puuid, score, rank? }] 배열이라고 보고, 그 밖의 모양이면 null 을 돌려 화면에서 숨긴다.
 * 형식이 정해지면 이 함수만 고치면 된다.
 */
export function parseDfScores(body: unknown): ScoreEntry[] | null {
  const data = (body as { data?: unknown } | null)?.data;
  const list = Array.isArray(data)
    ? data
    : (data as { participants?: unknown } | undefined)?.participants;
  if (!Array.isArray(list)) return null;

  const entries = list.filter(isScoreEntry);
  if (entries.length === 0) return null;

  // 순위가 안 오면 점수 높은 순으로 매긴다
  const ranked = [...entries].sort((a, b) => b.score - a.score);
  return entries.map((entry) => ({
    ...entry,
    rank: entry.rank ?? ranked.indexOf(entry) + 1,
  }));
}

// 같은 판을 여러 번 펼쳐도 한 번만 묻는다. 끝난 판의 점수는 바뀌지 않는다.
const cache = new Map<number, DfScores>();

/** 한 판의 DF 스코어를 참가자 번호별로 돌려준다. 형식을 알아볼 수 없으면 null. */
export async function fetchDfScores(game: LcuGame): Promise<DfScores | null> {
  const cached = cache.get(game.gameId);
  if (cached) return cached;

  const body = await apiPost<unknown>('/api/df-score', toDfScoreRequest(game));
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
