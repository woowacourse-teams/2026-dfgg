// 이 파일은 화면 쪽 타입(shared/types)에서도 불러 쓴다. Node 전용 모듈을 끌어오는 파일을 import 하면 안 된다.
import type { GameflowPhase, LcuSummonerProfile, Summoner } from '../../shared/types';

export const LCU_URI = {
  gameflowPhase: '/lol-gameflow/v1/gameflow-phase',
  // 프로필 아이콘·레벨·이름이 바뀌면 온다
  currentSummoner: '/lol-summoner/v1/current-summoner',
  // 프로필 배경을 바꾸면 온다
  summonerProfile: '/lol-summoner/v1/current-summoner/summoner-profile',
} as const;

/** 로그아웃처럼 값이 지워질 때는 data 가 null 로 온다. */
export type LcuEventMap = {
  [LCU_URI.gameflowPhase]: GameflowPhase;
  [LCU_URI.currentSummoner]: Summoner | null;
  [LCU_URI.summonerProfile]: LcuSummonerProfile | null;
};
