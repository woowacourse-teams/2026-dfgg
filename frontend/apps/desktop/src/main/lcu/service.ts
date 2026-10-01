import { getLockfileContent } from './lockfile';
import {
  getCurrentSummoner,
  getGameflowPhase,
  getGameVersion,
  getSummonerRankInfo,
  getMatchHistory,
  getMatchDetail,
  getGameflowSession,
  getEndOfGameStats,
} from './endpoints';
import type {
  MatchSummary,
  GameflowPhase,
  LcuCurrentRankedStats,
  Lockfile,
  Summoner,
  MatchDetail,
  EndedGame,
} from '../../shared/types';
import { toMatchSummaries, toMatchDetail } from './matchHistory';
import { getQueueNames } from './gameQueues';
import { reportError } from '../sentry';

async function withLockfile<T>(
  errorLabel: string,
  callback: (lockfileContent: Lockfile) => Promise<T>,
) {
  const lockfileContent = getLockfileContent();

  if (!lockfileContent) {
    console.error('클라이언트 연결 실패');
    return null;
  }

  try {
    return await callback(lockfileContent);
  } catch (error) {
    console.error(errorLabel, error);
    reportError('lcu-request', error);
    throw error;
  }
}

// lockfile 찾고 현재 소환사 정보 요청하기
export function fetchCurrentSummoner() {
  return withLockfile<Summoner | null>('소환사 정보 요청 실패', (lockfileContent) => {
    return getCurrentSummoner(lockfileContent);
  });
}

// 앱 최초 실행 시 현재 phase 가져오기
export function fetchGameflowPhase() {
  return withLockfile<GameflowPhase | null>('현재 game flow phase 요청 실패', (lockfileContent) => {
    return getGameflowPhase(lockfileContent);
  });
}

export function fetchGameVersion() {
  return withLockfile('패치버전 요청 실패', (lockfile) => getGameVersion(lockfile));
}

// 현재 소환사 랭크 정보 가져오기
export function fetchSummonerRankInfo() {
  return withLockfile<LcuCurrentRankedStats | null>('소환사 정보 요청 실패', (lockfile) => {
    return getSummonerRankInfo(lockfile);
  });
}

// 최근 20경기 전적 가져오기
export function fetchMatchHistory() {
  return withLockfile<MatchSummary[] | null>('전적 요청 실패', async (lockfile) => {
    // 큐 이름표는 캐시돼 있어서 두 번째 호출부터는 요청이 나가지 않는다.
    const [raw, queueNames] = await Promise.all([
      getMatchHistory(lockfile),
      getQueueNames(lockfile),
    ]);
    if (!raw) return null;

    return toMatchSummaries(raw, queueNames);
  });
}

// 전적 카드를 펼쳤을 때 10명 기록 가져오기
export function fetchMatchDetail(gameId: number) {
  return withLockfile<MatchDetail | null>('전적 상세 요청 실패', async (lockfile) => {
    const raw = await getMatchDetail(lockfile, gameId);
    if (!raw) return null;

    return toMatchDetail(raw);
  });
}

// 진행 중인 게임 정보 가져오기
export function fetchGameflowSession() {
  return withLockfile('게임 세션 요청 실패', (lockfile) => getGameflowSession(lockfile));
}

// 게임 결과 화면 정보 가져오기
export function fetchEndOfGameStats() {
  return withLockfile('게임 결과 요청 실패', (lockfile) => getEndOfGameStats(lockfile));
}

// 큐 id로 큐 이름 가져오기
export function fetchQueueName(queueId: number) {
  return withLockfile('큐 이름 요청 실패', async (lockfile) => {
    const queueNames = await getQueueNames(lockfile);
    return queueNames.get(queueId) ?? null;
  });
}

// 결과 화면에서 방금 끝난 게임 정보 모으기. 피드백에 같이 담는다.
export async function fetchEndedGame(): Promise<EndedGame | null> {
  const [stats, session, summoner] = await Promise.all([
    fetchEndOfGameStats(),
    fetchGameflowSession().catch(() => null),
    // 소환사 조회가 실패해도 피드백은 받는다.
    fetchCurrentSummoner().catch(() => null),
  ]);
  if (!stats) return null;

  const myTeam = stats.teams?.find((team) => team.isPlayerTeam);
  const queueId = session?.gameData.queue.id;

  return {
    riotId: summoner ? `${summoner.gameName}#${summoner.tagLine}` : undefined,
    gameId: stats.gameId,
    championId: stats.localPlayer?.championId,
    queue: queueId === undefined ? undefined : await fetchQueueName(queueId),
    result: myTeam ? (myTeam.isWinningTeam ? 'win' : 'lose') : 'unknown',
  };
}
