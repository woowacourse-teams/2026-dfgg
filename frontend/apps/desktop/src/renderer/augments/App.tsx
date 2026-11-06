import './style.css';

import { useEffect, useState } from 'react';

import type { MayhemSession } from '../../shared/types';
import { t } from '../i18n';
import { type AugmentCard, useAugmentCards } from './useAugmentCards';

/** 반짝이는 별 자리. 카드 크기 비율(%)과 나타나는 시차 */
const SPARKLES = [
  { x: -2, y: 8, delay: 0 },
  { x: 102, y: 22, delay: 0.5 },
  { x: 97, y: 96, delay: 1.1 },
  { x: 3, y: 74, delay: 1.6 },
  { x: 50, y: -2, delay: 0.8 },
  { x: 60, y: 101, delay: 2 },
];

/** 셋 중 픽률이 가장 높은 카드. 둘 이상 읽혀야 비교가 되므로 그 전에는 고르지 않는다 */
function bestIndex(cards: AugmentCard[]): number | null {
  const rated = cards
    .map((card, index) => ({ index, rate: card.augment?.pickRate ?? null }))
    .filter((entry): entry is { index: number; rate: number } => entry.rate !== null);
  if (rated.length < 2) return null;
  const top = Math.max(...rated.map((entry) => entry.rate));
  const winners = rated.filter((entry) => entry.rate === top);
  return winners.length === 1 ? winners[0].index : null;
}

function CardMark({ card, best }: { card: AugmentCard; best: boolean }) {
  const { augment } = card;
  // 읽는 중이거나 모르는 증강이면 아무것도 얹지 않는다. 틀린 정보보다 없는 게 낫다
  if (!augment) return null;

  const tier = t.mayhem.tiers[augment.rarity];
  const hasData = augment.pickRate !== null && augment.rank !== null;

  return (
    <div
      className={`aug-card ${best ? 'aug-card-best' : ''} aug-tier-${augment.rarity}`}
      style={{
        left: `${card.box.x * 100}%`,
        top: `${card.box.y * 100}%`,
        width: `${card.box.width * 100}%`,
        height: `${card.box.height * 100}%`,
      }}
    >
      {best && (
        <>
          <span className='aug-glow' aria-hidden='true' />
          <span className='aug-ring' aria-hidden='true' />
          {SPARKLES.map((spark) => (
            <i
              key={`${spark.x}-${spark.y}`}
              className='aug-spark'
              style={{ left: `${spark.x}%`, top: `${spark.y}%`, animationDelay: `${spark.delay}s` }}
            />
          ))}
        </>
      )}

      <p className='aug-badge'>
        {best && <b className='aug-best'>{t.mayhem.best}</b>}
        {hasData ? (
          <>
            <span className='aug-rank'>{t.mayhem.rank(tier, augment.rank!)}</span>
            <span className='aug-rate'>{t.mayhem.pickRate(augment.pickRate!.toFixed(1))}</span>
          </>
        ) : (
          <span className='aug-rank'>{t.mayhem.noData}</span>
        )}
      </p>
    </div>
  );
}

/** 게임 화면 위에 겹친 투명한 창. 증강 선택 카드가 보일 때만 그 위에 픽률을 얹는다 */
function App() {
  const [session, setSession] = useState<MayhemSession | null>(null);

  useEffect(() => {
    window.mayhem.getSession().then(setSession);
    return window.mayhem.onSessionChange(setSession);
  }, []);

  const cards = useAugmentCards(session);
  if (!cards) return null;
  const best = bestIndex(cards);

  return (
    <main className='aug-stage'>
      {cards.map((card, index) => (
        <CardMark key={index} card={card} best={index === best} />
      ))}
    </main>
  );
}

export default App;
