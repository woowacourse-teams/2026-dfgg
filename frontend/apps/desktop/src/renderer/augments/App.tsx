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

  // 지금 뜬 카드 중 순위가 가장 높은(숫자가 작은) 것을 강조한다
  const ranks = cards.flatMap(({ augment }) => (augment?.rank != null ? [augment.rank] : []));
  const bestRank = ranks.length > 0 ? Math.min(...ranks) : null;

  return (
    <>
      {cards.map(({ box, augment }, i) =>
        augment ? (
          <div
            key={i}
            className='augment-slot'
            style={{
              left: `${box.x * 100}%`,
              top: `${box.y * 100}%`,
              width: `${box.width * 100}%`,
            }}
          >
            {/* key 에 id 를 넣어 증강이 바뀌면 다시 떠오르게 한다 */}
            <div key={augment.augmentId} className={`augment-label${augment.rank !== null && augment.rank === bestRank ? ' is-best' : ''}`}>
              {augment.rank !== null && (
                <>
                  <span className='augment-stat'>
                    {RARITY_LABEL[augment.rarity]} <b>{augment.rank}위</b>
                  </span>
                  <i className='augment-sep' />
                </>
              )}
              <span className='augment-stat'>
                픽률 <b>{formatPickRate(augment.pickRate)}</b>
              </span>
            </div>
          </div>
        ) : null,
      )}
    </>
  );
}

export default App;
