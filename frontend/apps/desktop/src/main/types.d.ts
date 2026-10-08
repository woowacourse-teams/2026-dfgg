import type { GameflowPhase, LcuStatus, RecommendationUpdate, Summoner } from '../shared/types';

export type BroadcastChannels = {
  'lcu:status': LcuStatus;
  'lcu:phase': GameflowPhase | null;
  'lcu:summoner': Summoner;
  /** 프로필 배경으로 고른 스킨 id. 고른 게 없으면 null. */
  'lcu:profile-background': number | null;
  'lcu:items-recommendation': RecommendationUpdate | null;
};
