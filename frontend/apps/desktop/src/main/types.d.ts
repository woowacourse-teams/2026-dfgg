import type { GameflowPhase, LcuStatus, RecommendationUpdate } from '../shared/types';

export type BroadcastChannels = {
  'lcu:status': LcuStatus;
  'lcu:phase': GameflowPhase | null;
  'lcu:items-recommendation': RecommendationUpdate | null;
};
