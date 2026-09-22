import type { Summoner, Lockfile, GameflowPhase } from '../../types';
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
