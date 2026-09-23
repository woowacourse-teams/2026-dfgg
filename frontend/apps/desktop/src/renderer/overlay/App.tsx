import './style.css';

import { useEffect, useState } from 'react';

import type { RecommendedItem } from '../../shared/types';

function ItemRow({ item }: { item: RecommendedItem }) {
  const { traits } = item.description;

  return (
    <li className='item'>
      <img className='item-image' src={item.imageUrl} alt='' />
      <div className='item-body'>
        <span className='item-name'>{item.name}</span>
        {traits.length > 0 && (
          <ul className='traits'>
            {traits.map((trait) => (
              <li key={trait}>{trait}</li>
            ))}
          </ul>
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
