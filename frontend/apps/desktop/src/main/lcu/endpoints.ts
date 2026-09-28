import type { Summoner, Lockfile, GameflowPhase, LcuCurrentRankedStats } from '../../shared/types';
import { LcuGame, type LcuMatchHistory } from './matchHistory.types';
import { lcuRequest } from './client';
import type { LcuGameQueue } from './gameQueues.types';
import type { LcuEndOfGameStats, LcuGameflowSession } from './gameflow.types';

// 소환사 정보 얻는 api
export function getCurrentSummoner(lockfile: Lockfile) {
  return lcuRequest<Summoner>(lockfile, '/lol-summoner/v1/current-summoner');
}

// 현재 game flow phase 얻는 api
export function getGameflowPhase(lockfile: Lockfile) {
  return lcuRequest<GameflowPhase>(lockfile, '/lol-gameflow/v1/gameflow-phase');
}

// 패치 정보 얻기
export function getGameVersion(lockfile: Lockfile) {
  return lcuRequest<string>(lockfile, '/lol-patch/v1/game-version');
}

// 소환사 랭크 정보 얻기
export function getSummonerRankInfo(lockfile: Lockfile) {
  return lcuRequest<LcuCurrentRankedStats>(lockfile, '/lol-ranked/v1/current-ranked-stats');
}

// 20경기 전적 가져오기
export function getMatchHistory(lockfile: Lockfile) {
  return lcuRequest<LcuMatchHistory>(
    lockfile,
    '/lol-match-history/v1/products/lol/current-summoner/matches',
  );
}

// 큐 이름표 가져오기. 패치 때만 바뀌어서 gameQueues.ts 가 캐시해 둔다.
export function getGameQueues(lockfile: Lockfile) {
  return lcuRequest<LcuGameQueue[]>(lockfile, '/lol-game-queues/v1/queues');
}

// 게임에 참여한 유저 챔피언이 상세 기록 가져오기
export function getMatchDetail(lockfile: Lockfile, gameId: number) {
  return lcuRequest<LcuGame>(lockfile, `/lol-match-history/v1/games/${gameId}`);
}

// 진행 중인 게임 정보(gameId, 큐) 가져오기
export function getGameflowSession(lockfile: Lockfile) {
  return lcuRequest<LcuGameflowSession>(lockfile, '/lol-gameflow/v1/session');
}

// 게임 결과 화면 정보 가져오기
export function getEndOfGameStats(lockfile: Lockfile) {
  return lcuRequest<LcuEndOfGameStats>(lockfile, '/lol-end-of-game/v1/eog-stats-block');
}
