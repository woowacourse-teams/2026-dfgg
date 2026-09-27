// 인벤토리 목업 파일
import { AnimatePresence, motion } from 'motion/react';

import BorderBeam from '../../components/effects/BorderBeam';
import { itemIcon } from '../../lib';
import { INVENTORY_ORDER } from './demoData';

interface InventoryProps {
  count: number;
  isCentered: boolean;
}

export default function Inventory({ count, isCentered }: InventoryProps) {
  const isFull = count === INVENTORY_ORDER.length;

  return (
    <motion.div
      layout
      transition={{ type: 'spring', stiffness: 220, damping: 28 }}
      className={
        isCentered
          ? 'absolute top-[49%] left-1/2 -translate-x-1/2 md:top-[20%]'
          : 'absolute right-[4%] bottom-[36%] md:right-auto md:bottom-[6%] md:left-1/2 md:-translate-x-1/2'
      }
    >
      <AnimatePresence>
        {isFull && (
          <motion.div
            aria-hidden='true'
            initial={{ opacity: 0, scale: 0.6 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0 }}
            className='absolute -inset-10 rounded-3xl bg-rank-1/50 blur-3xl'
          />
        )}
      </AnimatePresence>
      <motion.ul
        layout
        aria-label={`인벤토리 ${count}칸 채워짐`}
        className='relative grid grid-cols-3 gap-1.5 rounded-lg bg-black/70 p-2 ring-1 ring-white/10'
      >
        {INVENTORY_ORDER.map((item, i) => (
          <motion.li
            layout
            key={item.id}
            className={`relative rounded-sm bg-white/5 ring-1 ring-white/10 ${
              isCentered ? 'size-14 sm:size-20 lg:size-24' : 'size-7 md:size-9'
            }`}
          >
            {i < count && (
              <>
                <motion.img
                  layoutId={`item-${item.id}`}
                  src={itemIcon(item.id)}
                  alt={item.name}
                  transition={{ type: 'spring', stiffness: 260, damping: 26 }}
                  className='size-full rounded-sm'
                />
                <motion.span
                  aria-hidden='true'
                  initial={{ opacity: 1, scale: 1 }}
                  animate={{ opacity: 0, scale: 1.6 }}
                  transition={{ duration: 0.6, delay: 0.35 }}
                  className='absolute inset-0 rounded-sm ring-2 ring-rank-1'
                />
              </>
            )}
          </motion.li>
        ))}
        {isFull && <BorderBeam size={120} duration={4} borderWidth={2} />}
      </motion.ul>
    </motion.div>
  );
}
