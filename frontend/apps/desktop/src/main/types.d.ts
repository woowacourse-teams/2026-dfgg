import type { GameflowPhase, LcuStatus, RecommendedItem } from '../shared/types';

export type BroadcastChannels = {
  'lcu:status': LcuStatus;
  'lcu:phase': GameflowPhase | null;
  'lcu:items-recommendation': RecommendedItem[] | null;
};
