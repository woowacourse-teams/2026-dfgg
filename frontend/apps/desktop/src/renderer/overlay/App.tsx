import './style.css';

import { useEffect, useState } from 'react';

import type { NamedEntry, RecommendedItem } from '../../shared/types';

const VARIANT_LABEL = {
  ally: '시너지',
  counter: '카운터',
} as const;

// 라벨(아군/상대)에 붙는 설명
const VARIANT_HINT = {
  ally: '이 아이템과 시너지가 좋은 아군 챔피언',
  counter: '이 아이템으로 상대하기 좋은 적 챔피언',
} as const;

// 초상화에 붙는 설명. 챔피언 이름과 관계를 한 줄에 담는다.
const VARIANT_RELATION = {
  ally: '아군 — 시너지 좋음',
  counter: '상대 — 카운터로 좋음',
} as const;

/**
 * 챔피언 이름은 안 띄우고 초상화만 보여준다.
 * 색만으로는 아군/적군 구분이 약해서 짧은 라벨을 함께 붙인다.
 */
function ChampionIcons({
  champions,
  variant,
}: {
  champions: NamedEntry[];
  variant: 'ally' | 'counter';
}) {
  if (champions.length === 0) return null;

  return (
    <div className={`reason reason-${variant}`}>
      <span className='reason-label' title={VARIANT_HINT[variant]}>
        {VARIANT_LABEL[variant]}
      </span>
      <ul className='champions'>
        {champions.map((champion) => (
          <li key={champion.id}>
            {/* 이름을 안 띄우므로 alt·title 로 정보를 남긴다.
                title 은 가장 가까운 것만 뜨므로 관계까지 여기에 함께 적는다. */}
            <img
              className='champion-icon'
              src={champion.imageUrl}
              alt={`${champion.name} — ${VARIANT_RELATION[variant]}`}
              title={`${champion.name} — ${VARIANT_RELATION[variant]}`}
            />
          </li>
        ))}
      </ul>
    </div>
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
        {traits.length > 0 && (
          <p className='traits' title={traits.join(' · ')}>
            {traits.join(' · ')}
          </p>
        )}
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
          <button
            type='button'
            className='control control-logo'
            aria-label='오버레이 켜기'
            onClick={toggleCollapsed}
          >
            <img className='logo' src='./icon.png' alt='' />
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
          <button
            type='button'
            className='control control-off'
            aria-label='오버레이 끄기'
            onClick={toggleCollapsed}
          >
            ×
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
