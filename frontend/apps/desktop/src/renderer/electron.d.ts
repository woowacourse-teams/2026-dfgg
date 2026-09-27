import type {
  GameflowPhase,
  LcuCurrentRankedStats,
  LcuState,
  LcuStatus,
  RecommendationUpdate,
  Summoner,
} from '../shared/types';

export {};

type StatusListener = (status: LcuStatus) => void;
type PhaseListener = (phase: GameflowPhase) => void;
type ItemsListener = (items: RecommendationUpdate) => void;
type Unsubscribe = () => void;

declare global {
  interface Window {
    lcu: {
      currentSummoner: () => Promise<Summoner | null>;
      getRankInfo: () => Promise<LcuCurrentRankedStats | null>;
      getState: () => Promise<LcuState>;
      onStatusChange: (callback: StatusListener) => Unsubscribe;
      onPhaseChange: (callback: PhaseListener) => Unsubscribe;
      onItemsRecommendationChange: (callback: ItemsListener) => Unsubscribe;
    };
    windowControls: {
      setCollapsed: (collapsed: boolean) => void;
      minimize: () => void;
      close: () => void;
    };
  }
}
