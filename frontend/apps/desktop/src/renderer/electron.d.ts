import type {
  EndedGame,
  FeedbackRating,
  GameflowPhase,
  LcuCurrentRankedStats,
  LcuState,
  LcuStatus,
  MatchDetail,
  MatchSummary,
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
      /** 클라이언트에서 고른 프로필 배경 스킨 id. 없으면 null. */
      getProfileBackground: () => Promise<number | null>;
      /** 롤 클라이언트를 띄운다. 실행 파일을 못 찾으면 false. */
      launchClient: () => Promise<boolean>;
      getState: () => Promise<LcuState>;
      getMatchHistoryInfo: () => Promise<MatchSummary[] | null>;
      getMatchDetail: (gameId: number) => Promise<MatchDetail | null>;
      onStatusChange: (callback: StatusListener) => Unsubscribe;
      onPhaseChange: (callback: PhaseListener) => Unsubscribe;
      /** 클라이언트에서 프로필 아이콘·이름·레벨이 바뀌면 불린다. */
      onSummonerChange: (callback: (summoner: Summoner) => void) => Unsubscribe;
      /** 클라이언트에서 프로필 배경을 바꾸면 불린다. */
      onProfileBackgroundChange: (callback: (skinId: number | null) => void) => Unsubscribe;
      onItemsRecommendationChange: (callback: ItemsListener) => Unsubscribe;
      getEndedGame: () => Promise<EndedGame | null>;
    };
    windowControls: {
      setCollapsed: (collapsed: boolean) => void;
      minimize: () => void;
      close: () => void;
    };
    analytics: {
      track: (name: string, data?: Record<string, string | number | boolean>) => void;
    };
    feedback: {
      submit: (rating: FeedbackRating, game: EndedGame) => Promise<Record<string, string>>;
    };
  }
}
