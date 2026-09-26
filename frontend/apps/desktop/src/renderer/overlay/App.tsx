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

const TOP_COUNT = 3;

function ItemRow({ item, rank }: { item: RecommendedItem; rank: number }) {
  const { traits, ally, counter } = item.description;
  const hasChampions = ally.length > 0 || counter.length > 0;
  const isTop = rank <= TOP_COUNT;

  return (
    <li className={isTop ? `item item-top item-rank-${rank}` : 'item item-rest'}>
      {/* 상위 3개만 순위를 붙인다. 나머지는 자리로만 순서를 표현. */}
      {isTop && (
        <span className='rank' aria-label={`추천 ${rank}순위`}>
          {rank}
        </span>
      )}

      <img className='item-image' src={item.imageUrl} alt={item.name} title={item.name} />

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
  const [purchasedCount, setPurchasedCount] = useState<number | null>(null);

  const coreIndex = purchasedCount === null ? null : purchasedCount + 1;

  useEffect(() => {
    window.lcu.getState().then((state) => {
      setItems(state.recommendations);
      setIsInGame(state.phase === 'InProgress');
      setPurchasedCount(state.purchasedCount ?? null);
    });

    const unsubscribeItems = window.lcu.onItemsRecommendationChange(({ items, purchasedCount }) => {
      setItems(items);
      setPurchasedCount(purchasedCount);
    });

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
        <span className='title'>
          {coreIndex === null ? '추천 아이템' : `${coreIndex}코어 추천`}
        </span>

        <div className='window-controls'>
          <button type='button' className='control' aria-label='최소화' onClick={toggleCollapsed}>
            {'<'}
          </button>
        </div>
      </header>

      <main className='content'>
        {items && items.length > 0 ? (
          <ul className='items'>
            {items.map((item, index) => (
              <ItemRow key={item.id} item={item} rank={index + 1} />
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
