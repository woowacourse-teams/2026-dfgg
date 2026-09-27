import './App.css';

import { useEffect, useState } from 'react';

import type { GameflowPhase, LcuCurrentRankedStats, LcuStatus, Summoner } from '../../shared/types';
import ProfileHeader from './components/ProfileHeader/ProfileHeader';
import RankSection from './components/RankSection/RankSection';

function App() {
  const [currentSummoner, setCurrentSummoner] = useState<Summoner | null>(null);
  const [lcuState, setLcuState] = useState<LcuStatus | null>(null);
  const [lcuPhase, setLcuPhase] = useState<GameflowPhase | null>(null);
  const [rankInfo, setRankInfo] = useState<LcuCurrentRankedStats | null>(null);

  // 연결 상태와 현재 phase를 가져온다.
  useEffect(() => {
    window.lcu.getState().then((state) => {
      setLcuState(state.status);
      setLcuPhase(state.phase);
    });

    const unsubscribeStatus = window.lcu.onStatusChange(setLcuState);
    const unsubscribePhase = window.lcu.onPhaseChange(setLcuPhase);

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
      .catch(() => console.error('소환사 정보를 불러오지 못했습니다.'));

    return () => {
      cancelled = true;
    };
  }, [lcuState]);

  // 랭크는 게임이 끝났을 때만 바뀌므로 그 시점에만 다시 받는다.
  useEffect(() => {
    if (lcuPhase !== 'None' && lcuPhase !== 'EndOfGame') return;

    window.lcu.getRankInfo().then(setRankInfo);
  }, [lcuPhase]);

  if (!currentSummoner) {
    return (
      <div className='app'>
        <p className='empty'>소환사 정보를 불러오는 중...</p>
      </div>
    );
  }

  return (
    <div className='app'>
      <ProfileHeader summoner={currentSummoner} status={lcuState} phase={lcuPhase} />

      <main className='content'>
        <RankSection rankInfo={rankInfo} />
      </main>
    </div>
  );
}

export default App;
