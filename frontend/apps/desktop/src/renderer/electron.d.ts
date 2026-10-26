import { Locale } from '../shared/i18n';
import type {
  ChampSelectState,
  DfScores,
  EndedGame,
  FeedbackRating,
  GameflowPhase,
  HighlightGame,
  HighlightProgress,
  HighlightSummary,
  LcuCurrentRankedStats,
  LcuState,
  LcuStatus,
  MatchDetail,
  MatchSummary,
  OverlayClickTarget,
  Position,
  RecommendationUpdate,
  Summoner,
} from '../shared/types';

export {};

type StatusListener = (status: LcuStatus) => void;
type PhaseListener = (phase: GameflowPhase) => void;
type ItemsListener = (items: RecommendationUpdate) => void;
type HighlightProgressListener = (progress: HighlightProgress) => void;
type Unsubscribe = () => void;

declare global {
  interface Window {
    /** main 이 정한 화면 언어('ko' | 'en'). 브라우저에서 devMock 으로 열면 없다. */
    appLocale?: string | null;
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
      /** 한 판의 DF 스코어 (참가자 번호별). 응답을 알아볼 수 없으면 null. */
      getDfScores: (gameId: number) => Promise<DfScores | null>;
      onStatusChange: (callback: StatusListener) => Unsubscribe;
      onPhaseChange: (callback: PhaseListener) => Unsubscribe;
      /** 클라이언트에서 프로필 아이콘·이름·레벨이 바뀌면 불린다. */
      onSummonerChange: (callback: (summoner: Summoner) => void) => Unsubscribe;
      /** 클라이언트에서 프로필 배경을 바꾸면 불린다. */
      onProfileBackgroundChange: (callback: (skinId: number | null) => void) => Unsubscribe;
      onItemsRecommendationChange: (callback: ItemsListener) => Unsubscribe;
      getEndedGame: () => Promise<EndedGame | null>;
    };
    champSelect: {
      /** 밴픽 중이 아니면 null */
      getState: () => Promise<ChampSelectState | null>;
      onStateChange: (callback: (state: ChampSelectState | null) => void) => Unsubscribe;
      /** 추천 페이지(rank)를 클라이언트 룬 페이지에 넣는다 */
      applyPage: (rank: number) => void;
      /** 오늘의 픽 선수 룬을 그대로 넣는다 */
      applyProPage: (playerName: string) => void;
      /** 배정 포지션이 없는 큐에서 추천받을 포지션을 고른다 */
      pickPosition: (position: Position) => void;
    };
    windowControls: {
      setCollapsed: (collapsed: boolean) => void;
      /** 오버레이 내용 높이(px)를 알려 창 높이를 맞춘다. */
      setOverlayHeight: (height: number) => void;
      minimize: () => void;
      close: () => void;
    };
    analytics: {
      track: (name: string, data?: Record<string, string | number | boolean>) => void;
      overlayClick: (target: OverlayClickTarget) => void;
    };
    feedback: {
      submit: (rating: FeedbackRating, game: EndedGame) => Promise<Record<string, string>>;
    };
    highlight: {
      list: () => Promise<HighlightGame[]>;
      summary: (gameIds: number[]) => Promise<Record<number, HighlightSummary>>;
      get: (gameId: number) => Promise<HighlightGame | null>;
      onProgress: (callback: HighlightProgressListener) => Unsubscribe;
    };
    settings: {
      getLocale: Locale;
      setLocale: (locale: Locale) => Promise<void>;
      getAutoLaunch: () => Promise<boolean>;
      setAutoLaunch: (enabled: boolean) => Promise<void>;
      getAutoDocking: () => Promise<boolean>;
      setAutoDocking: (enabled: boolean) => Promise<void>;
      getOverlayShow: () => Promise<boolean>;
      setOverlayShow: (enabled: boolean) => Promise<void>;
      getHighlightEnabled: () => Promise<boolean>;
      setHighlightEnabled: (enabled: boolean) => Promise<void>;
      getHighlightRoot: () => string;
      openHighlightFolder: () => Promise<string>;
      getHighlightMaxClipCount: () => Promise<number>;
      setHighlightMaxClipCount: (count: number) => Promise<number>;
      getAppVersion: () => string;
      openPrivacyPolicy: () => Promise<void>;
    };
  }
}
