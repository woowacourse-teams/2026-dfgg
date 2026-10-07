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
import FeedbackModal from './components/feedback/FeedbackModal';
import MatchSection from './components/MatchSection/MatchSection';
import ProfileHeader from './components/ProfileHeader/ProfileHeader';
import QueueTabs from './components/QueueTabs/QueueTabs';
import { useDdragonVersion } from './lib/ddragon';
import { filterMatches, QUEUE_FILTERS, type QueueFilter } from './lib/queues';
import { summarizeMatches } from './lib/recentSummary';

// 소환사 정보가 없는 이유를 상태별로 구분해 알린다.
// 클라이언트가 꺼져 있는데 '불러오는 중' 이라고 하면 오해를 준다.
const EMPTY_TEXT: Record<LcuStatus, { title: string; hint: string }> = {
  disconnected: {
    title: '롤 클라이언트를 실행해 주세요',
    hint: '클라이언트가 켜지면 알아서 연결돼요',
  },
  connecting: { title: '클라이언트에 연결하는 중', hint: '잠시만 기다려 주세요' },
  connected: { title: '소환사 정보를 불러오는 중', hint: '잠시만 기다려 주세요' },
};

function App() {
  const [currentSummoner, setCurrentSummoner] = useState<Summoner | null>(null);
  const [lcuState, setLcuState] = useState<LcuStatus | null>(null);
  const [lcuPhase, setLcuPhase] = useState<GameflowPhase | null>(null);
  const [rankInfo, setRankInfo] = useState<LcuCurrentRankedStats | null>(null);
  const [matches, setMatches] = useState<MatchSummary[] | null>(null);
  const [endedGame, setEndedGame] = useState<EndedGame | null>(null);
  const [queue, setQueue] = useState<QueueFilter>('all');
  const version = useDdragonVersion();

  const playedGame = useRef(false); // 앱을 켠 뒤 한 게임을 한 판이라도 했는지 확인
  const askedFeedback = useRef(false); // 이번 실행에서 피드백을 이미 했는지 확인

  const closeFeedback = useCallback(() => setEndedGame(null), []);

  // 탭은 실제로 한 판이라도 있는 종류만 보여 준다.
  const tabs = useMemo(
    () =>
      QUEUE_FILTERS.map(({ key, label }) => ({
        key,
        label,
        count: filterMatches(matches ?? [], key).length,
      })).filter((tab) => tab.count > 0),
    [matches],
  );
  // 전적이 새로 오면서 고른 종류가 사라졌으면 전체로 돌아간다.
  const activeQueue = tabs.some((tab) => tab.key === queue) ? queue : 'all';

  const shownMatches = useMemo(
    () => (matches ? filterMatches(matches, activeQueue) : null),
    [matches, activeQueue],
  );
  const summary = useMemo(
    () => (shownMatches ? summarizeMatches(shownMatches) : null),
    [shownMatches],
  );
  // 배경 그림은 탭을 바꿔도 그대로 둔다. 누를 때마다 프로필 그림이 바뀌면 어지럽다.
  const favoriteChampionId = useMemo(
    () => (matches ? summarizeMatches(matches)?.champions[0]?.championId : undefined),
    [matches],
  );
  const emptyText = EMPTY_TEXT[lcuState ?? 'disconnected'];

  // 연결 상태와 현재 phase를 가져온다.
  useEffect(() => {
    window.lcu.getState().then((state) => {
      setLcuState(state.status);
      setLcuPhase(state.phase);

      // 게임 중에 앱을 켜면 InProgress 이벤트가 화면이 뜨기 전에 지나가서 여기서도 표시한다.
      if (state.phase === 'InProgress') playedGame.current = true;
    });

    const unsubscribeStatus = window.lcu.onStatusChange(setLcuState);
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
      <ProfileHeader summoner={currentSummoner} backdropChampionId={favoriteChampionId} />

      {currentSummoner ? (
        <main className='content'>
          {/* 탭이 '전체' 하나뿐이면 고를 게 없다 */}
          {tabs.length > 2 && <QueueTabs tabs={tabs} active={activeQueue} onChange={setQueue} />}
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
        <main className='empty'>
          <div className='empty-mark'>
            <img src='./icon.png' alt='' />
          </div>
          <p className='empty-title'>{emptyText.title}</p>
          <p className='empty-hint'>{emptyText.hint}</p>
        </main>
      )}
      {endedGame && (
        <FeedbackModal key={endedGame.gameId} game={endedGame} onClose={closeFeedback} />
      )}
    </div>
  );
}

export default App;
