import { useState } from 'react';

import { ChevronIcon } from '../icons';
import {
  AVAILABLE_GOLD,
  type Champion,
  type CoreItem,
  CORES,
  PURCHASED_CORES,
  SITUATIONAL,
  type SituationalItem,
} from './data';
import { championIcon } from './icons';

type IconOf = (id: number) => string | undefined;

const BUILD_SLOTS = 6;
const formatGold = (gold: number) => `${gold.toLocaleString('ko-KR')}G`;

function Faces({ champions, tone }: { champions: Champion[]; tone: 'ally' | 'counter' }) {
  if (champions.length === 0) return null;
  return (
    <span className={`faces faces-${tone}`}>
      {champions.map((champion) => (
        <img
          key={champion.id}
          src={championIcon(champion.id)}
          alt={champion.name}
          title={champion.name}
        />
      ))}
    </span>
  );
}

function Bar({ title }: { title: string }) {
  return (
    <header className='bar'>
      <span className='bar-title'>{title}</span>
      <ol className='progress'>
        {Array.from({ length: BUILD_SLOTS }, (_, slot) => (
          <li
            key={slot}
            className={
              slot < PURCHASED_CORES
                ? 'progress-done'
                : slot === PURCHASED_CORES
                  ? 'progress-next'
                  : ''
            }
          />
        ))}
      </ol>
      <span className='pp-gold' title='보유 골드'>
        {formatGold(AVAILABLE_GOLD)}
      </span>
      <button type='button' className='bar-button' aria-label='접기'>
        <ChevronIcon />
      </button>
    </header>
  );
}

/** 하위 아이템 묶음. 구매 순서대로 화살표로 잇고 끝에 드는 골드를 붙인다. */
function Bundle({ core, icon, size }: { core: CoreItem; icon: IconOf; size: 'large' | 'small' }) {
  const bundle = core.purchaseBundle;
  if (!bundle) return <span className='pp-bundle-none'>지금 살 재료 없음</span>;

  const ordered = bundle.purchaseOrder
    .map((id) => bundle.items.find((item) => item.itemId === id))
    .filter((item) => item !== undefined);

  return (
    <span className={`pp-bundle pp-bundle-${size}`}>
      {ordered.map((item, index) => (
        <span key={item.itemId} className='pp-bundle-step'>
          {index > 0 && <i aria-hidden='true'>›</i>}
          <img src={icon(item.itemId)} alt={item.name} title={item.name} />
          {item.quantity > 1 && <b>×{item.quantity}</b>}
        </span>
      ))}
      <span className='pp-cost'>{formatGold(bundle.additionalCost)}</span>
    </span>
  );
}

function SituationalRow({ item, icon }: { item: SituationalItem; icon: IconOf }) {
  return (
    <li className={`pp-situ ${item.affordable ? '' : 'pp-situ-locked'}`}>
      <img className='pp-situ-art' src={icon(item.id)} alt='' />
      <span className='pick-body'>
        <span className='pick-name'>{item.name}</span>
        <span className='pick-traits'>{item.traits.join(' · ')}</span>
      </span>
      <Faces champions={item.counter} tone='counter' />
      <span className={`pp-cost ${item.affordable ? 'pp-cost-ok' : 'pp-cost-short'}`}>
        {item.affordable
          ? formatGold(item.additionalCost)
          : `${formatGold(item.additionalCost - AVAILABLE_GOLD)} 부족`}
      </span>
    </li>
  );
}

/** A안: 지금 구조에 묶음을 붙인다. 1순위 카드 안에 '지금 살 것' 줄이 생기고, 상황템은 맨 아래에 따로 둔다. */
export function OverlayA({ icon }: { icon: IconOf }) {
  const [best, ...rest] = CORES;

  return (
    <div className='overlay pp-overlay'>
      <Bar title={`${PURCHASED_CORES + 1}코어 추천`} />

      <ol className='picks'>
        <li className='pick pick-best pp-best' style={{ '--order': 0 } as React.CSSProperties}>
          <div className='pp-best-top'>
            <span className='pick-art'>
              <img src={icon(best.id)} alt='' />
              <b className='pick-rank'>1</b>
            </span>
            <span className='pick-body'>
              <span className='pick-name'>{best.name}</span>
              <span className='pick-traits'>{best.traits.join(' · ')}</span>
              <span className='pick-reasons'>
                <Faces champions={best.ally} tone='ally' />
                <Faces champions={best.counter} tone='counter' />
              </span>
            </span>
          </div>
          <div className='pp-now'>
            <span className='pp-now-label'>지금 살 것</span>
            <Bundle core={best} icon={icon} size='large' />
          </div>
        </li>

        {rest.map((core, index) => (
          <li
            key={core.id}
            className='pick'
            style={{ '--order': index + 1 } as React.CSSProperties}
          >
            <span className='pick-art'>
              <img src={icon(core.id)} alt='' />
              <b className='pick-rank'>{index + 2}</b>
            </span>
            <span className='pick-body'>
              <span className='pick-name'>{core.name}</span>
              <Bundle core={core} icon={icon} size='small' />
            </span>
          </li>
        ))}
      </ol>

      <section className='pp-situ-section'>
        <h3>상대 조합 대응</h3>
        <ul>
          {SITUATIONAL.map((item) => (
            <SituationalRow key={item.id} item={item} icon={icon} />
          ))}
        </ul>
      </section>
    </div>
  );
}

/** B안: '지금 뭘 사야 하나'를 맨 위에 둔다. 1순위 코어는 그 목적지로만 작게 보인다. */
export function OverlayB({ icon }: { icon: IconOf }) {
  const [best, ...rest] = CORES;
  const bundle = best.purchaseBundle;

  return (
    <div className='overlay pp-overlay'>
      <Bar title='지금 사기' />

      <section className='pp-shop'>
        {bundle && (
          <ol className='pp-shop-steps'>
            {bundle.purchaseOrder.map((id, index) => {
              const item = bundle.items.find((entry) => entry.itemId === id);
              if (!item) return null;
              return (
                <li key={id}>
                  <span className='pp-shop-art'>
                    <img src={icon(id)} alt='' />
                    <b>{index + 1}</b>
                  </span>
                  <span className='pp-shop-name'>{item.name}</span>
                </li>
              );
            })}
            <li className='pp-shop-total'>
              <span>합계</span>
              <strong>{formatGold(bundle.additionalCost)}</strong>
            </li>
          </ol>
        )}
        <p className='pp-shop-goal'>
          <img src={icon(best.id)} alt='' />
          <span>
            목표 <b>{best.name}</b>
          </span>
        </p>
      </section>

      <ol className='picks pp-next'>
        <li className='pp-next-label'>다음 코어</li>
        {rest.map((core, index) => (
          <li
            key={core.id}
            className='pick pick-rest'
            style={{ '--order': index } as React.CSSProperties}
          >
            <span className='pick-art'>
              <img src={icon(core.id)} alt='' />
            </span>
            <span className='pick-body'>
              <span className='pick-name'>{core.name}</span>
            </span>
            {core.purchaseBundle ? (
              <span className='pp-cost'>{formatGold(core.purchaseBundle.additionalCost)}</span>
            ) : (
              <span className='pp-bundle-none'>골드 부족</span>
            )}
          </li>
        ))}
      </ol>

      <ul className='pp-alerts'>
        {SITUATIONAL.map((item) => (
          <li key={item.id} className={item.affordable ? '' : 'pp-alert-locked'}>
            <Faces champions={item.counter.slice(0, 1)} tone='counter' />
            <span>
              <b>{item.counter[0]?.name}</b> 대응 · {item.name}
            </span>
            <span className='pp-cost'>{formatGold(item.additionalCost)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** C안: 코어와 상황템을 탭으로 나눈다. 한 화면에 하나만 보여서 가장 덜 붐빈다. */
export function OverlayC({ icon, tab }: { icon: IconOf; tab: 'core' | 'situational' }) {
  const [open, setOpen] = useState<number | null>(CORES[0]?.id ?? null);

  return (
    <div className='overlay pp-overlay'>
      <Bar title={`${PURCHASED_CORES + 1}코어 추천`} />
      <div className='pp-tabs' role='tablist'>
        <button type='button' role='tab' aria-selected={tab === 'core'}>
          코어 <b>{CORES.length}</b>
        </button>
        <button type='button' role='tab' aria-selected={tab === 'situational'}>
          상황템 <b>{SITUATIONAL.length}</b>
        </button>
      </div>

      {tab === 'core' ? (
        <ol className='picks'>
          {CORES.map((core, index) => {
            const expanded = open === core.id;
            return (
              <li
                key={core.id}
                className={`pick pp-fold ${expanded ? 'pp-fold-open' : ''} ${index === 0 ? 'pp-fold-best' : ''}`}
                style={{ '--order': index } as React.CSSProperties}
              >
                <button
                  type='button'
                  className='pp-fold-head'
                  onClick={() => setOpen(expanded ? null : core.id)}
                >
                  <span className='pick-art'>
                    <img src={icon(core.id)} alt='' />
                    <b className='pick-rank'>{index + 1}</b>
                  </span>
                  <span className='pick-body'>
                    <span className='pick-name'>{core.name}</span>
                    <span className='pick-traits'>{core.traits.join(' · ')}</span>
                  </span>
                  {core.purchaseBundle ? (
                    <span className='pp-cost'>
                      {formatGold(core.purchaseBundle.additionalCost)}
                    </span>
                  ) : (
                    <span className='pp-bundle-none'>부족</span>
                  )}
                </button>
                {expanded && (
                  <div className='pp-fold-body'>
                    <Bundle core={core} icon={icon} size='large' />
                    <span className='pick-reasons'>
                      <Faces champions={core.ally} tone='ally' />
                      <Faces champions={core.counter} tone='counter' />
                    </span>
                  </div>
                )}
              </li>
            );
          })}
        </ol>
      ) : (
        <ul className='pp-situ-list'>
          {SITUATIONAL.map((item) => (
            <SituationalRow key={item.id} item={item} icon={icon} />
          ))}
        </ul>
      )}
    </div>
  );
}
