import './App.css';

import * as Sentry from '@sentry/electron/renderer';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import type {
  EndedGame,
  GameflowPhase,
  LcuCurrentRankedStats,
  LcuStatus,
  MatchSummary,
  Summoner,
} from '../../shared/types';
import Bento from './components/Bento/Bento';
import EmptyState from './components/EmptyState/EmptyState';
import FeedbackModal from './components/feedback/FeedbackModal';
import MatchSection from './components/MatchSection/MatchSection';
import ProfileHeader from './components/ProfileHeader/ProfileHeader';
import QueueTabs from './components/QueueTabs/QueueTabs';
import { championSplashUrl, skinSplashUrl, useDdragonVersion } from './lib/ddragon';
import { filterMatches, QUEUE_FILTERS, type QueueFilter } from './lib/queues';
import { summarizeMatches } from './lib/recentSummary';

function App() {
  const [currentSummoner, setCurrentSummoner] = useState<Summoner | null>(null);
  const [lcuState, setLcuState] = useState<LcuStatus | null>(null);
  const [lcuPhase, setLcuPhase] = useState<GameflowPhase | null>(null);
  const [rankInfo, setRankInfo] = useState<LcuCurrentRankedStats | null>(null);
  const [backgroundSkinId, setBackgroundSkinId] = useState<number | null>(null);
  const [matches, setMatches] = useState<MatchSummary[] | null>(null);
  const [endedGame, setEndedGame] = useState<EndedGame | null>(null);
  const [queue, setQueue] = useState<QueueFilter>('all');
  const version = useDdragonVersion();

  const playedGame = useRef(false); // 앱을 켠 뒤 한 게임을 한 판이라도 했는지 확인
  const askedFeedback = useRef(false); // 이번 실행에서 피드백을 이미 했는지 확인

  const closeFeedback = useCallback(() => setEndedGame(null), []);

  // 메뉴는 늘 같은 자리에 같은 칸이 있어야 한다. 판수가 0인 종류도 칸은 남기고 못 누르게만 한다.
  // '기타'만은 해당 판이 있을 때만 보인다.
  const tabs = useMemo(
    () =>
      QUEUE_FILTERS.map(({ key, label }) => ({
        key,
        label,
        count: filterMatches(matches ?? [], key).length,
      })).filter((tab) => tab.key !== 'etc' || tab.count > 0),
    [matches],
  );
  // 전적이 새로 오면서 고른 종류의 판이 사라졌으면 전체로 돌아간다.
  const activeQueue = tabs.some((tab) => tab.key === queue && tab.count > 0) ? queue : 'all';

  const shownMatches = useMemo(
    () => (matches ? filterMatches(matches, activeQueue) : null),
    [matches, activeQueue],
  );
  const summary = useMemo(
    () => (shownMatches ? summarizeMatches(shownMatches) : null),
    [shownMatches],
  );
  // 배너는 클라이언트에서 고른 프로필 배경을 그대로 쓴다. 고른 적이 없으면 가장 많이 한 챔피언으로 대신한다.
  // 탭을 바꿔도 그대로 둔다. 누를 때마다 프로필 그림이 바뀌면 어지럽다.
  const backdropUrl = useMemo(() => {
    if (backgroundSkinId) return skinSplashUrl(backgroundSkinId);

    const favorite = matches ? summarizeMatches(matches)?.champions[0]?.championId : undefined;
    return favorite === undefined ? undefined : championSplashUrl(favorite);
  }, [backgroundSkinId, matches]);

  // 연결 상태와 현재 phase를 가져온다.
  useEffect(() => {
    window.lcu.getState().then((state) => {
      setLcuState(state.status);
      setLcuPhase(state.phase);

      // 게임 중에 앱을 켜면 InProgress 이벤트가 화면이 뜨기 전에 지나가서 여기서도 표시한다.
      if (state.phase === 'InProgress') playedGame.current = true;
    });

    const unsubscribeStatus = window.lcu.onStatusChange(setLcuState);
    // 클라이언트에서 프로필 아이콘이나 배경을 바꾸면 바로 따라 바뀐다.
    const unsubscribeSummoner = window.lcu.onSummonerChange(setCurrentSummoner);
    const unsubscribeBackground = window.lcu.onProfileBackgroundChange(setBackgroundSkinId);
    const unsubscribePhase = window.lcu.onPhaseChange((phase) => {
      setLcuPhase(phase);

      if (phase === 'InProgress') {
        playedGame.current = true;
        setEndedGame(null);
      }
      if (phase === 'EndOfGame' && playedGame.current && !askedFeedback.current) {
        askedFeedback.current = true;
        window.lcu
          .getEndedGame()
          .then(setEndedGame)
          .catch((error) => {
            console.error('끝난 게임 정보를 불러오지 못했습니다.');
            Sentry.captureException(error);
          });
      }
    });

    return () => {
      unsubscribeStatus();
      unsubscribeSummoner();
      unsubscribeBackground();
      unsubscribePhase();
    };
  }, []);

  // state 정보가 바뀔 때만 소환사 정보를 가져온다.
  useEffect(() => {
    if (lcuState !== 'connected') return;

    let cancelled = false;

    window.lcu
      .currentSummoner()
      .then((summoner) => {
        if (!cancelled) setCurrentSummoner(summoner);
      })
      .catch((error) => {
        console.error('소환사 정보를 불러오지 못했습니다.');
        Sentry.captureException(error);
      });

    // 프로필 배경은 못 받아도 화면은 그려야 하므로 실패하면 조용히 대체 그림을 쓴다.
    window.lcu
      .getProfileBackground()
      .then((skinId) => {
        if (!cancelled) setBackgroundSkinId(skinId);
      })
      .catch((error) => Sentry.captureException(error));

    return () => {
      cancelled = true;
    };
  }, [lcuState]);

  // 랭크와 전적은 게임이 끝났을 때만 바뀌므로 그 시점에만 다시 받는다.
  useEffect(() => {
    if (lcuPhase !== 'None' && lcuPhase !== 'EndOfGame') return;

    window.lcu.getRankInfo().then(setRankInfo);
    window.lcu
      .getMatchHistoryInfo()
      .then(setMatches)
      .catch((error) => {
        console.error('전적을 불러오지 못했습니다.');
        Sentry.captureException(error);
      });
  }, [lcuPhase]);

  return (
    <div className='app'>
      <ProfileHeader summoner={currentSummoner} backdropUrl={backdropUrl} />

      {currentSummoner ? (
        <main className='content'>
          <QueueTabs tabs={tabs} active={activeQueue} onChange={setQueue} />
          {/* 탭을 바꾸면 통계 판을 새로 그려 숫자가 다시 세어 올라가게 한다 */}
          <section className='bento-section'>
            {summary && (
              <div className='section-head'>
                <h1 className='section-title'>최근 {summary.games}판 요약</h1>
              </div>
            )}
            <Bento key={activeQueue} summary={summary} rankInfo={rankInfo} />
          </section>
          <MatchSection matches={shownMatches} version={version} />
        </main>
      ) : (
        <EmptyState status={lcuState} />
      )}
      {endedGame && (
        <FeedbackModal key={endedGame.gameId} game={endedGame} onClose={closeFeedback} />
      )}
    </div>
  );
}

export default App;
