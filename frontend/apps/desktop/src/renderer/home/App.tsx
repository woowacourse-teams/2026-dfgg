import './App.css';

import * as Sentry from '@sentry/electron/renderer';
import { useCallback, useEffect, useRef, useState } from 'react';

import type {
  EndedGame,
  GameflowPhase,
  LcuCurrentRankedStats,
  LcuStatus,
  MatchSummary,
  Summoner,
} from '../../shared/types';
import FeedbackModal from './components/feedback/FeedbackModal';
import MatchSection from './components/MatchSection/MatchSection';
import ProfileHeader from './components/ProfileHeader/ProfileHeader';
import RankSection from './components/RankSection/RankSection';

// 소환사 정보가 없는 이유를 상태별로 구분해 알린다.
// 클라이언트가 꺼져 있는데 '불러오는 중' 이라고 하면 오해를 준다.
const EMPTY_TEXT: Record<LcuStatus, string> = {
  disconnected: '롤 클라이언트를 실행해 주세요',
  connecting: '클라이언트에 연결하는 중...',
  connected: '소환사 정보를 불러오는 중...',
};

function App() {
  const [currentSummoner, setCurrentSummoner] = useState<Summoner | null>(null);
  const [lcuState, setLcuState] = useState<LcuStatus | null>(null);
  const [lcuPhase, setLcuPhase] = useState<GameflowPhase | null>(null);
  const [rankInfo, setRankInfo] = useState<LcuCurrentRankedStats | null>(null);
  const [matches, setMatches] = useState<MatchSummary[] | null>(null);
  const [endedGame, setEndedGame] = useState<EndedGame | null>(null);

  const playedGame = useRef(false); // 앱을 켠 뒤 한 게임을 한 판이라도 했는지 확인
  const askedFeedback = useRef(false); // 이번 실행에서 피드백을 이미 했는지 확인

  const closeFeedback = useCallback(() => setEndedGame(null), []);

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
      <ProfileHeader summoner={currentSummoner} status={lcuState} phase={lcuPhase} />

      <main className='content'>
        {currentSummoner ? (
          <>
            <RankSection rankInfo={rankInfo} />
            <MatchSection matches={matches} />
          </>
        ) : (
          <p className='empty'>{EMPTY_TEXT[lcuState ?? 'disconnected']}</p>
        )}
      </main>
      {endedGame && (
        <FeedbackModal key={endedGame.gameId} game={endedGame} onClose={closeFeedback} />
      )}
    </div>
  );
}

export default App;
