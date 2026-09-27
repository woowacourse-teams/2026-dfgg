import {
  type Summoner,
  type Lockfile,
  type GameflowPhase,
  LcuCurrentRankedStats,
} from '../../shared/types';
import { lcuRequest } from './client';

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
