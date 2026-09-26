import './App.css';

import { useEffect, useState } from 'react';

import type { LcuStatus, NamedEntry, RecommendedItem, Summoner } from '../../shared/types';

const STATUS_TEXT: Record<LcuStatus, string> = {
  disconnected: '롤 클라이언트 대기 중',
  connecting: '연결 중',
  connected: '연결됨',
};

function ChampionChip({ champion }: { champion: NamedEntry }) {
  return (
    <li className='chip'>
      <img className='chip-image' src={champion.imageUrl} alt='' />
      <span>{champion.name}</span>
    </li>
  );
}

function ItemCard({ item }: { item: RecommendedItem }) {
  const { counter, ally, traits } = item.description;

  return (
    <article className='card'>
      <header className='card-header'>
        <img className='item-image' src={item.imageUrl} alt='' />
        <h2 className='item-name'>{item.name}</h2>
      </header>

      {traits.length > 0 && (
        <ul className='traits'>
          {traits.map((trait) => (
            <li key={trait}>{trait}</li>
          ))}
        </ul>
      )}

      {counter.length > 0 && (
        <section className='reason'>
          <h3 className='reason-title'>상대하기 좋은 챔피언</h3>
          <ul className='chips'>
            {counter.map((champion) => (
              <ChampionChip key={champion.id} champion={champion} />
            ))}
          </ul>
        </section>
      )}

      {ally.length > 0 && (
        <section className='reason'>
          <h3 className='reason-title'>시너지 좋은 아군</h3>
          <ul className='chips'>
            {ally.map((champion) => (
              <ChampionChip key={champion.id} champion={champion} />
            ))}
          </ul>
        </section>
      )}
    </article>
  );
}

function App() {
  const [currentSummoner, setCurrentSummoner] = useState<Summoner | null>(null);
  const [lcuState, setLcuState] = useState<LcuStatus | null>(null);
  const [lcuPhase, setLcuPhase] = useState<string | null>(null);
  const [items, setItems] = useState<RecommendedItem[] | null>(null);

  useEffect(() => {
    window.lcu.getState().then((state) => {
      setLcuState(state.status);
      setLcuPhase(state.phase);
    });

    const unsubscribeStatus = window.lcu.onStatusChange(setLcuState);
    const unsubscribePhase = window.lcu.onPhaseChange(setLcuPhase);
    const unsubscribeItems = window.lcu.onItemsRecommendationChange(({ items }) => {
      setItems(items);
    });

    return () => {
      unsubscribeStatus();
      unsubscribePhase();
      unsubscribeItems();
    };
  }, []);

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

  const isInGame = lcuPhase === 'InProgress';

  return (
    <div className='app'>
      <header className='status-bar'>
        <span className={`badge badge-${lcuState ?? 'unknown'}`}>
          {lcuState ? STATUS_TEXT[lcuState] : '확인 중'}
        </span>
        <span className='status-item'>{lcuPhase ?? '-'}</span>
        <span className='status-item status-summoner'>
          {lcuState === 'connected' && currentSummoner
            ? `${currentSummoner.gameName}#${currentSummoner.tagLine}`
            : '-'}
        </span>
      </header>

      <main className='content'>
        {items && items.length > 0 ? (
          <div className='cards'>
            {items.map((item) => (
              <ItemCard key={item.id} item={item} />
            ))}
          </div>
        ) : (
          <p className='empty'>
            {isInGame ? '추천 아이템을 기다리는 중입니다.' : '게임에 입장하면 아이템을 추천합니다.'}
          </p>
        )}
      </main>
    </div>
  );
}

export default App;
