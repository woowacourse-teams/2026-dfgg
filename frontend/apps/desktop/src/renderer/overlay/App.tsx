import './style.css';

import { useEffect, useState } from 'react';

import type { NamedEntry, RecommendedItem } from '../../shared/types';

/** 이름 없이 초상화만. 아군/적군은 테두리 색으로 구분한다. */
function ChampionIcons({
  champions,
  variant,
}: {
  champions: NamedEntry[];
  variant: 'ally' | 'counter';
}) {
  if (champions.length === 0) return null;

  return (
    <ul className={`champions champions-${variant}`}>
      {champions.map((champion) => (
        <li key={champion.id}>
          {/* 이름을 안 띄우므로 alt·title 로 정보를 남긴다 */}
          <img
            className='champion-icon'
            src={champion.imageUrl}
            alt={champion.name}
            title={champion.name}
          />
        </li>
      ))}
    </ul>
  );
}

function ItemRow({ item }: { item: RecommendedItem }) {
  const { traits, ally, counter } = item.description;
  const hasChampions = ally.length > 0 || counter.length > 0;

  return (
    <li className='item'>
      <img className='item-image' src={item.imageUrl} alt={item.name} title={item.name} />

      {/* 설명과 챔피언은 남는 폭을 전부 쓴다. 빈 것은 렌더하지 않아 빈칸이 안 생긴다. */}
      <div className='item-body'>
        {traits.length > 0 && <p className='traits'>{traits.join(' · ')}</p>}
        {hasChampions && (
          <div className='reasons'>
            <ChampionIcons champions={ally} variant='ally' />
            <ChampionIcons champions={counter} variant='counter' />
          </div>
        )}
      </div>
    </li>
  );
}

function App() {
  const [items, setItems] = useState<RecommendedItem[] | null>(null);
  const [isInGame, setIsInGame] = useState(false);
  const [isCollapsed, setIsCollapsed] = useState(false);

  useEffect(() => {
    window.lcu.getState().then((state) => {
      setItems(state.recommendations);
      setIsInGame(state.phase === 'InProgress');
    });

    const unsubscribeItems = window.lcu.onItemsRecommendationChange(setItems);
    const unsubscribePhase = window.lcu.onPhaseChange((phase) =>
      setIsInGame(phase === 'InProgress'),
    );

    return () => {
      unsubscribeItems();
      unsubscribePhase();
    };
  }, []);

  const toggleCollapsed = () => {
    const next = !isCollapsed;
    setIsCollapsed(next);
    window.windowControls.setCollapsed(next);
  };

  if (isCollapsed) {
    return (
      <div className='app collapsed'>
        <header className='title-bar'>
          <button type='button' className='control' aria-label='펼치기' onClick={toggleCollapsed}>
            {'>'}
          </button>
        </header>
      </div>
    );
  }

  return (
    <div className='app'>
      <header className='title-bar'>
        <span className='title'>추천 아이템</span>

        <div className='window-controls'>
          <button type='button' className='control' aria-label='최소화' onClick={toggleCollapsed}>
            {'<'}
          </button>
        </div>
      </header>

      <main className='content'>
        {items && items.length > 0 ? (
          <ul className='items'>
            {items.map((item) => (
              <ItemRow key={item.id} item={item} />
            ))}
          </ul>
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
