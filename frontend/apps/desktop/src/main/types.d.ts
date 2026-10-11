import type {
  GameflowPhase,
  HighlightProgress,
  LcuStatus,
  RecommendationUpdate,
} from '../shared/types';

export type BroadcastChannels = {
  'lcu:status': LcuStatus;
  'lcu:phase': GameflowPhase | null;
  'lcu:items-recommendation': RecommendationUpdate | null;
  'highlight:progress': HighlightProgress;
};
