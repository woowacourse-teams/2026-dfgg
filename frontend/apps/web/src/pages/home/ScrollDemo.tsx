// 스크롤 데모 파일
import {
  AnimatePresence,
  motion,
  type MotionValue,
  useMotionValueEvent,
  useReducedMotion,
  useScroll,
  useSpring,
  useTransform,
} from 'motion/react';
import { useEffect, useRef, useState } from 'react';

import { track, trackOnce } from '../../lib';
import { INVENTORY_ORDER, recommendationsAfter, stageAt, STAGES } from './demoData';
import Inventory from './Inventory';
import OverlayMock from './OverlayMock';

const VIDEO_SRC = '/media/gameplay.mp4';
const VIDEO_POSTER = '/media/gameplay-poster.jpg';
const EXPAND_END = 0.16;
const STAGE = { purchase: 1, reason: 2, finale: 3 } as const;
const FULL_AT = 0.75;

const countFor = (stage: number, sp: number) => {
  if (stage < STAGE.purchase) return 0;
  if (stage < STAGE.finale) return 1;
  const local = (sp - STAGES[STAGE.finale].start) / (1 - STAGES[STAGE.finale].start);
  return Math.min(INVENTORY_ORDER.length, 1 + Math.floor((local / FULL_AT) * 5));
};

const smoothstep = (edge0: number, edge1: number, x: number) => {
  const t = Math.min(Math.max((x - edge0) / (edge1 - edge0), 0), 1);
  return t * t * (3 - 2 * t);
};
const toStageProgress = (p: number) => (p - EXPAND_END) / (1 - EXPAND_END);

function RailSegment({ index, progress, isCurrent, onSelect }: RailSegmentProps) {
  const start = STAGES[index].start;
  const end = STAGES[index + 1]?.start ?? 1;
  const fill = useTransform(progress, (p) => smoothstep(start, end, toStageProgress(p)));

  return (
    <button
      type='button'
      data-umami-event='demo-stage-click'
      data-umami-event-stage={STAGES[index].label}
      onClick={() => onSelect(index)}
      aria-current={isCurrent ? 'step' : undefined}
      className='group flex-1 cursor-pointer pt-3 text-left focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent'
    >
      <span className='block h-0.5 overflow-hidden rounded-full bg-white/20'>
        <motion.span className='block h-full origin-left bg-white' style={{ scaleX: fill }} />
      </span>
      <span
        className={`mt-2 block text-xs transition-colors sm:text-sm ${
          isCurrent ? 'text-white' : 'text-white/50 group-hover:text-white/80'
        }`}
      >
        {STAGES[index].label}
      </span>
    </button>
  );
}

interface RailSegmentProps {
  index: number;
  progress: MotionValue<number>;
  isCurrent: boolean;
  onSelect: (index: number) => void;
}

export default function ScrollDemo() {
  const sectionRef = useRef<HTMLElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const [stage, setStage] = useState(0);
  const [isHovered, setIsHovered] = useState(false);
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [inventoryCount, setInventoryCount] = useState(0);

  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ['start start', 'end end'],
  });
  const progress = useSpring(scrollYProgress, { stiffness: 160, damping: 32, restDelta: 0.0005 });

  const startInsetX = typeof window !== 'undefined' && window.innerWidth < 640 ? 4 : 22;
  const expand = useTransform(progress, (p) => smoothstep(0, EXPAND_END, p));
  const clipPath = useTransform(expand, (e) => {
    const x = startInsetX * (1 - e);
    const top = 6 * (1 - e);
    const bottom = 18 * (1 - e);
    return `inset(${top}% ${x}% ${bottom}% ${x}% round ${24 * (1 - e)}px)`;
  });
  const tilt = useTransform(expand, [0, 1], [24, 0]);
  const mediaScale = useTransform(expand, [0, 1], [1.3, 1]);
  const titleOpacity = useTransform(progress, [0, EXPAND_END * 0.7], [1, 0]);
  const titleY = useTransform(progress, [0, EXPAND_END * 0.7], [0, -32]);
  const uiOpacity = useTransform(progress, [EXPAND_END * 0.8, EXPAND_END * 1.2], [0, 1]);

  useMotionValueEvent(scrollYProgress, 'change', (p) => {
    const sp = toStageProgress(p);
    const next = stageAt(sp);
    setStage(next);
    const count = countFor(next, sp);
    setInventoryCount(count);

    if (p > 0.02) trackOnce('demo-enter');
    if (sp >= 0) trackOnce('demo-stage-view', { stage: STAGES[next].label });
    if (count === INVENTORY_ORDER.length) trackOnce('demo-full-build');
  });

  const prefersReducedMotion = useReducedMotion();

  useEffect(() => {
    const section = sectionRef.current;
    const video = videoRef.current;
    if (!section || !video || prefersReducedMotion) return;

    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        video.play().catch(() => video.pause());
      } else {
        video.pause();
      }
    });
    observer.observe(section);
    return () => observer.disconnect();
  }, [prefersReducedMotion]);

  const scrollToStage = (index: number) => {
    const el = sectionRef.current;
    if (!el) return;
    const top = el.getBoundingClientRect().top + window.scrollY;
    const scrollable = el.offsetHeight - window.innerHeight;
    const p = EXPAND_END + (STAGES[index].start + 0.03) * (1 - EXPAND_END);
    window.scrollTo({ top: top + p * scrollable, behavior: 'smooth' });
  };

  const isFull = inventoryCount === INVENTORY_ORDER.length;
  const current = STAGES[stage];

  return (
    <section
      id='demo'
      ref={sectionRef}
      aria-labelledby='demo-heading'
      className='relative h-[600vh] [overflow-anchor:none]'
    >
      <div className='sticky top-0 h-svh overflow-hidden [perspective:1400px]'>
        <motion.div
          style={{ clipPath, rotateX: tilt }}
          className='absolute inset-0 origin-bottom bg-surface'
        >
          <motion.video
            ref={videoRef}
            src={VIDEO_SRC}
            poster={VIDEO_POSTER}
            muted
            loop
            playsInline
            preload='metadata'
            onError={() => track('demo-video-error')}
            aria-hidden='true'
            style={{ scale: mediaScale }}
            className='absolute inset-0 size-full object-cover'
          />
          <motion.div
            aria-hidden='true'
            style={{ opacity: expand }}
            className='absolute inset-0 bg-[linear-gradient(to_top,rgb(0_0_0/0.9),rgb(0_0_0/0.35)_40%,transparent_65%),linear-gradient(to_right,rgb(0_0_0/0.45),transparent_55%)]'
          />

          <motion.div
            aria-hidden='true'
            style={{ opacity: titleOpacity }}
            className='absolute inset-0 bg-black/40'
          />

          <motion.div style={{ opacity: uiOpacity }} className='absolute inset-0'>
            <AnimatePresence>
              {!isFull && (
                <motion.div
                  key='overlay'
                  exit={{ opacity: 0, x: -24 }}
                  transition={{ duration: 0.4 }}
                  onMouseEnter={() => {
                    setIsHovered(true);
                    trackOnce('demo-overlay-hover');
                  }}
                  onMouseLeave={() => setIsHovered(false)}
                  className='absolute top-[14%] left-[4%] origin-top-left scale-[0.72] sm:top-[16%] sm:scale-90 md:scale-100 lg:scale-110'
                >
                  <OverlayMock
                    coreIndex={inventoryCount + 1}
                    items={recommendationsAfter(inventoryCount)}
                    isActive={isHovered || stage === STAGE.reason}
                    isCollapsed={isCollapsed}
                    onToggle={() => setIsCollapsed((collapsed) => !collapsed)}
                  />
                </motion.div>
              )}
            </AnimatePresence>

            <Inventory count={inventoryCount} isCentered={stage === STAGE.finale} />

            <div className='absolute bottom-[6%] left-[4%] w-[min(36rem,92%)]'>
              <div className='min-h-40 sm:min-h-44'>
                <AnimatePresence mode='wait'>
                  <motion.div
                    key={stage}
                    initial={{ opacity: 0, y: 16 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -8 }}
                    transition={{ duration: 0.35, ease: [0.16, 1, 0.3, 1] }}
                    aria-live='polite'
                  >
                    <h3 className='text-2xl leading-snug font-bold tracking-[-0.02em] text-balance text-white sm:text-4xl'>
                      {current.title}
                    </h3>
                    <p className='mt-3 leading-relaxed whitespace-pre-line text-white/75 sm:text-lg'>
                      {current.body}
                    </p>
                  </motion.div>
                </AnimatePresence>
              </div>
              <nav aria-label='데모 단계' className='mt-5 flex gap-3'>
                {STAGES.map((s, i) => (
                  <RailSegment
                    key={s.label}
                    index={i}
                    progress={scrollYProgress}
                    isCurrent={i === stage}
                    onSelect={scrollToStage}
                  />
                ))}
              </nav>
            </div>
          </motion.div>
        </motion.div>

        <motion.h2
          id='demo-heading'
          style={{ opacity: titleOpacity, y: titleY }}
          className='pointer-events-none absolute inset-0 flex items-center justify-center px-6 text-center text-4xl leading-tight font-bold tracking-[-0.03em] text-white [text-shadow:0_2px_24px_rgb(0_0_0/0.6)] sm:text-6xl'
        >
          <span>
            게임 중엔
            <br />
            이렇게 뜹니다!
          </span>
        </motion.h2>
      </div>
    </section>
  );
}
