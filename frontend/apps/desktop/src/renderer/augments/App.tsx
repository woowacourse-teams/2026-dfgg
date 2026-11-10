import { useEffect, useState } from 'react';

import type { AugmentRarity, MayhemCard } from '../../shared/types';

const RARITY_LABEL: Record<AugmentRarity, string> = {
  silver: '실버',
  gold: '골드',
  prismatic: '프리즘',
};

// pickRate 는 백엔드가 이미 퍼센트로 준다 (예: 16.8)
function formatPickRate(rate: number | null) {
  return rate === null ? '-' : `${rate.toFixed(1)}%`;
}

function App() {
  const [cards, setCards] = useState<MayhemCard[]>([]);

  useEffect(() => {
    window.mayhem.getCards().then(setCards);
    return window.mayhem.onCards(setCards);
  }, []);

  return (
    <>
      {cards.map(({ box, augment }, i) =>
        augment ? (
          <div
            key={i}
            className='augment-label'
            style={{
              left: `${box.x * 100}%`,
              top: `${box.y * 100}%`,
              width: `${box.width * 100}%`,
            }}
          >
            <strong>{augment.name}</strong>
            <span>
              {augment.rank !== null ? `${RARITY_LABEL[augment.rarity]} ${augment.rank}위` : '-'} · 픽률{' '}
              {formatPickRate(augment.pickRate)}
            </span>
          </div>
        ) : null,
      )}
    </>
  );
}

export default App;
