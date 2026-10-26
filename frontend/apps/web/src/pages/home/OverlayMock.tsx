// 인게임 오버레이 목업 파일. 데스크톱 앱 overlay/App.tsx 의 생김새를 그대로 따른다.
import './overlayMock.css';

import { motion } from 'motion/react';
import type { CSSProperties } from 'react';

import Logo from '../../assets/icon.png';
import { strings } from '../../i18n/i18n';
import { championIcon, itemIcon } from '../../lib';
import {
  CHAMPION_NAMES,
  DEMO_GOLD,
  type DemoBundle,
  type DemoItem,
  type DemoSituational,
} from './demoData';

const text = strings().overlay;

/** 순위 숫자를 붙이는 개수. 그 아래는 자리로만 순서를 알린다. */
const TOP_COUNT = 3;
/** 한 판에 채우는 아이템 칸 수 */
const BUILD_SLOTS = 6;

const formatGold = (gold: number) => `${gold.toLocaleString('ko-KR')}G`;

type Variant = 'ally' | 'counter';

const VARIANT_LABEL: Record<Variant, string> = { ally: text.synergy, counter: text.counter };
const VARIANT_RELATION: Record<Variant, string> = {
  ally: text.allyRelation,
  counter: text.counterRelation,
};

function Bundle({ bundle, size }: { bundle: DemoBundle; size: 'large' | 'small' }) {
  return (
    <span className={`bundle bundle-${size}`}>
      {bundle.parts.map((part, index) => (
        <span key={part.id} className='bundle-step'>
          {index > 0 && <i aria-hidden='true'>›</i>}
          <img src={itemIcon(part.id)} alt={part.name} title={part.name} />
          {part.quantity > 1 && <b>×{part.quantity}</b>}
        </span>
      ))}
      <span className='cost'>{formatGold(bundle.cost)}</span>
    </span>
  );
}

function Faces({
  ids,
  variant,
  withLabel = false,
}: {
  ids: string[];
  variant: Variant;
  withLabel?: boolean;
}) {
  if (ids.length === 0) return null;

  return (
    <span className={`faces faces-${variant}`}>
      {withLabel && <span className='faces-label'>{VARIANT_LABEL[variant]}</span>}
      {ids.map((id) => (
        <img
          key={id}
          src={championIcon(id)}
          alt={`${CHAMPION_NAMES[id]} — ${VARIANT_RELATION[variant]}`}
          title={`${CHAMPION_NAMES[id]} — ${VARIANT_RELATION[variant]}`}
        />
      ))}
    </span>
  );
}

function Pick({ item, rank }: { item: DemoItem; rank: number }) {
  const isBest = rank === 1;
  const traits = item.traits.join(' · ');

  // 산 아이템이 인벤토리로 날아가도록 그림에 layoutId 를 붙인다.
  const art = (
    <span className='pick-art'>
      <motion.img layoutId={`item-${item.id}`} src={itemIcon(item.id)} alt='' />
      {rank <= TOP_COUNT && (
        <b className='pick-rank' aria-label={text.rank(rank)}>
          {rank}
        </b>
      )}
    </span>
  );

  if (isBest) {
    return (
      <li className='pick pick-best' style={{ '--order': 0 } as CSSProperties}>
        <span className='pick-main'>
          {art}
          <span className='pick-body'>
            <span className='pick-name'>{item.name}</span>
            {traits && <span className='pick-traits'>{traits}</span>}
            {(item.ally.length > 0 || item.counter.length > 0) && (
              <span className='pick-reasons'>
                <Faces ids={item.ally} variant='ally' withLabel />
                <Faces ids={item.counter} variant='counter' withLabel />
              </span>
            )}
          </span>
        </span>
        {item.bundle && (
          <span className='now-buy'>
            <span className='now-buy-label'>{text.nowBuy}</span>
            <Bundle bundle={item.bundle} size='large' />
          </span>
        )}
      </li>
    );
  }

  return (
    <li
      className={`pick ${rank > TOP_COUNT ? 'pick-rest' : ''}`}
      style={{ '--order': rank - 1 } as CSSProperties}
    >
      {art}
      <span className='pick-body'>
        <span className='pick-name'>{item.name}</span>
        {item.bundle ? (
          <Bundle bundle={item.bundle} size='small' />
        ) : (
          traits && <span className='pick-traits'>{traits}</span>
        )}
      </span>
      <span className='pick-faces'>
        <Faces ids={item.ally} variant='ally' />
        <Faces ids={item.counter} variant='counter' />
      </span>
    </li>
  );
}

function SituationalRow({ item }: { item: DemoSituational }) {
  const shortage = item.cost - DEMO_GOLD;

  return (
    <li className={`situ ${item.isAffordable ? '' : 'situ-locked'}`}>
      <img className='situ-art' src={itemIcon(item.id)} alt='' />
      <span className='pick-body'>
        <span className='pick-name'>{item.name}</span>
        {item.traits.length > 0 && <span className='pick-traits'>{item.traits.join(' · ')}</span>}
      </span>
      <Faces ids={item.counter} variant='counter' />
      <span className={`cost ${item.isAffordable ? '' : 'cost-short'}`}>
        {item.isAffordable ? formatGold(item.cost) : text.shortage(formatGold(shortage))}
      </span>
    </li>
  );
}

/** 지금 몇 번째 아이템을 살 차례인지 빗금 여섯 칸으로 보여 준다. */
function BuildProgress({ purchased }: { purchased: number }) {
  return (
    <ol className='progress' aria-label={text.progress(BUILD_SLOTS, purchased)}>
      {Array.from({ length: BUILD_SLOTS }, (_, slot) => (
        <li
          key={slot}
          className={slot < purchased ? 'progress-done' : slot === purchased ? 'progress-next' : ''}
        />
      ))}
    </ol>
  );
}

interface OverlayMockProps {
  purchased: number;
  cores: DemoItem[];
  situational: DemoSituational[];
  isActive: boolean;
  isCollapsed: boolean;
  onToggle: () => void;
}

export default function OverlayMock({
  purchased,
  cores,
  situational,
  isActive,
  isCollapsed,
  onToggle,
}: OverlayMockProps) {
  if (isCollapsed) {
    return (
      <div className='overlay-mock overlay-collapsed'>
        <button
          type='button'
          aria-label={text.expand}
          data-umami-event='demo-overlay-expand'
          onClick={onToggle}
        >
          <img src={Logo} alt='' width={20} height={20} />
        </button>
      </div>
    );
  }

  return (
    <div className={`overlay-mock overlay ${isActive ? 'overlay-active' : ''}`}>
      <header className='bar'>
        <span className='bar-title'>{text.coreTitle(purchased + 1)}</span>
        <BuildProgress purchased={purchased} />
        <button
          type='button'
          className='bar-button'
          aria-label={text.collapse}
          data-umami-event='demo-overlay-collapse'
          onClick={onToggle}
        >
          {/* 데스크톱 icons.tsx 의 ChevronIcon 과 같은 모양 */}
          <svg
            width='12'
            height='12'
            viewBox='0 0 12 12'
            fill='none'
            stroke='currentColor'
            strokeWidth='1.2'
            strokeLinecap='round'
            strokeLinejoin='round'
            aria-hidden='true'
          >
            <path d='M3 4.5l3 3 3-3' />
          </svg>
        </button>
      </header>

      {/* 추천이 바뀌면 목록을 새로 그려 위에서부터 다시 차례로 나타나게 한다. */}
      <ol className='picks' key={cores.map((item) => item.id).join()}>
        {cores.map((item, index) => (
          <Pick key={item.id} item={item} rank={index + 1} />
        ))}
      </ol>

      {situational.length > 0 && (
        <section className='situ-section' aria-label={text.situational}>
          <h4>{text.situational}</h4>
          <ul>
            {situational.map((item) => (
              <SituationalRow key={item.id} item={item} />
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
