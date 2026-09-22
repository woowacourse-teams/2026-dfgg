import type { GameflowPhase, LcuState, LcuStatus, RecommendedItem, Summoner } from '../types';

export {};

type StatusListener = (status: LcuStatus) => void;
type PhaseListener = (phase: GameflowPhase) => void;
type ItemsListener = (items: RecommendedItem[]) => void;
type Unsubscribe = () => void;

declare global {
  interface Window {
    lcu: {
      currentSummoner: () => Promise<Summoner | null>;
      getState: () => Promise<LcuState>;
      onStatusChange: (callback: StatusListener) => Unsubscribe;
      onPhaseChange: (callback: PhaseListener) => Unsubscribe;
      onItemsRecommendationChange: (callback: ItemsListener) => Unsubscribe;
    };
  }
}
