// 인게임 오버레이 목업 파일
import { AnimatePresence, motion } from 'motion/react';

import Logo from '../../assets/icon.png';
import BorderBeam from '../../components/effects/BorderBeam';
import { strings } from '../../i18n/i18n';
import { championIcon, itemIcon } from '../../lib';
import { CHAMPION_NAMES, type DemoItem } from './demoData';

const text = strings();

const TOP_COUNT = 3;
const RANK_STYLE = [
  'border-rank-1 [&_.rank]:bg-rank-1',
  'border-rank-2 [&_.rank]:bg-rank-2',
  'border-rank-3 [&_.rank]:bg-rank-3',
];

interface ChampionsProps {
  ids: string[];
  variant: 'ally' | 'counter';
}

function Champions({ ids, variant }: ChampionsProps) {
  if (ids.length === 0) return null;
  const isAlly = variant === 'ally';

  return (
    <div className='flex items-center gap-[5px]'>
      <span
        className={`text-[10px] font-bold tracking-[-0.02em] ${isAlly ? 'text-[#8cc2ff]' : 'text-[#ff9b8d]'}`}
      >
        {isAlly ? text.overlay.synergy : text.overlay.counter}
      </span>
      <ul className='flex gap-1'>
        {ids.map((id) => (
          <li key={id}>
            <img
              src={championIcon(id)}
              alt={CHAMPION_NAMES[id]}
              width={26}
              height={26}
              className={`size-[26px] rounded-full border-2 ${
                isAlly
                  ? 'border-[#5aa2ff] shadow-[0_0_4px_rgb(90_162_255/0.6)]'
                  : 'border-[#f4705f] shadow-[0_0_4px_rgb(244_112_95/0.6)]'
              }`}
            />
          </li>
        ))}
      </ul>
    </div>
  );
}

function ItemRow({ item, rank, isActive }: { item: DemoItem; rank: number; isActive: boolean }) {
  const isTop = rank <= TOP_COUNT;
  const hasChampions = item.ally.length > 0 || item.counter.length > 0;

  return (
    <motion.li
      layout
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.35 }}
      className={`flex min-h-[58px] items-center gap-2 rounded-md border-l-[3px] p-1.5 ${
        isTop
          ? `${RANK_STYLE[rank - 1]} ${isActive ? 'bg-white/8' : 'bg-black/42'}`
          : `border-transparent pl-[30px] ${isActive ? 'bg-white/8 opacity-85' : 'bg-black/20 opacity-55'}`
      }`}
    >
      {isTop && (
        <span className='rank grid size-4 shrink-0 place-items-center rounded-full text-[10px] leading-none font-extrabold text-[#10131a] [text-shadow:none]'>
          {rank}
        </span>
      )}
      <motion.img
        layoutId={`item-${item.id}`}
        src={itemIcon(item.id)}
        alt={item.name}
        width={36}
        height={36}
        className='size-9 shrink-0 rounded'
      />
      <div className='flex min-w-0 flex-col gap-[5px]'>
        <p className='truncate text-[11px] leading-[1.35] text-white/80'>
          {item.traits.join(' · ')}
        </p>
        {hasChampions && (
          <div className='flex flex-wrap gap-x-2.5 gap-y-1'>
            <Champions ids={item.ally} variant='ally' />
            <Champions ids={item.counter} variant='counter' />
          </div>
        )}
      </div>
    </motion.li>
  );
}

interface OverlayMockProps {
  coreIndex: number;
  items: DemoItem[];
  isActive: boolean;
  isCollapsed: boolean;
  onToggle: () => void;
}

export default function OverlayMock({
  coreIndex,
  items,
  isActive,
  isCollapsed,
  onToggle,
}: OverlayMockProps) {
  if (isCollapsed) {
    return (
      <div className='grid size-10 place-items-center rounded-[10px] bg-black/30'>
        <button
          type='button'
          aria-label={text.overlay.expand}
          data-umami-event='demo-overlay-expand'
          onClick={onToggle}
          className='size-6 cursor-pointer rounded-md p-0 hover:bg-white/20'
        >
          <img src={Logo} alt='' width={24} height={24} className='size-full rounded' />
        </button>
      </div>
    );
  }

  return (
    <div
      className={`relative flex w-[260px] flex-col overflow-hidden rounded-[10px] text-white transition-colors duration-150 [text-shadow:0_1px_2px_rgb(0_0_0/0.95),0_0_6px_rgb(0_0_0/0.8)] ${
        isActive ? 'bg-overlay' : 'bg-[rgb(10_14_28/0.22)]'
      }`}
    >
      <div
        className={`flex h-10 shrink-0 items-center justify-between pl-5 transition-colors duration-150 ${
          isActive ? 'bg-black/70' : 'bg-black/30'
        }`}
      >
        <AnimatePresence mode='wait' initial={false}>
          <motion.span
            key={coreIndex}
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.2 }}
            className='text-xs font-semibold'
          >
            {text.overlay.coreTitle(coreIndex)}
          </motion.span>
        </AnimatePresence>
        <button
          type='button'
          aria-label={text.overlay.collapse}
          data-umami-event='demo-overlay-collapse'
          onClick={onToggle}
          className='h-full w-10 cursor-pointer text-base text-white/85 hover:bg-[rgb(244_112_95/0.35)] hover:text-white'
        >
          ×
        </button>
      </div>

      <ul className='flex flex-col gap-1.5 p-2'>
        {items.map((item, index) => (
          <ItemRow key={item.id} item={item} rank={index + 1} isActive={isActive} />
        ))}
      </ul>
      <BorderBeam />
    </div>
  );
}
