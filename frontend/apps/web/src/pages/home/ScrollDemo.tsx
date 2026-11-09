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
import { memo, useCallback, useEffect, useRef, useState } from 'react';

import { strings } from '../../i18n/i18n';
import { track, trackOnce } from '../../lib';
import { INVENTORY_ORDER, recommendationsAfter, stageAt, STAGES } from './demoData';
import Inventory from './Inventory';
import OverlayMock from './OverlayMock';

const text = strings();

const VIDEO_SRC = '/media/gameplay.mp4';
const VIDEO_POSTER = '/media/gameplay-poster.webp';
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

// 단계가 바뀔 때마다 ScrollDemo 가 다시 그려지는데, 칸 네 개는 자기 isCurrent 가 바뀔 때만 다시 그리면 된다.
const RailSegment = memo(function RailSegment({
  index,
  progress,
  isCurrent,
  onSelect,
}: RailSegmentProps) {
  const start = STAGES[index].start;
  const end = STAGES[index + 1]?.start ?? 1;
  const fill = useTransform(progress, (p) => smoothstep(start, end, toStageProgress(p)));

  return (
    <button
      type='button'
      data-umami-event='demo-stage-click'
      data-umami-event-stage={STAGES[index].key}
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
});

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
  // 오버레이 목업은 챔피언·아이템 그림을 수십 장 받는다. 데모에 들어오기 전에는 안 보이므로 그때 그린다.
  const [hasEntered, setHasEntered] = useState(false);
  // 저절로 계속 도는 영상은 멈출 수 있어야 한다(WCAG 2.2.2). 사용자가 멈췄으면 다시 화면에 들어와도 틀지 않는다.
  const [isVideoPaused, setIsVideoPaused] = useState(false);
  const isVideoPausedRef = useRef(false);

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
    if (p > 0) setHasEntered(true);

    if (p > 0.02) trackOnce('demo-enter');
    if (sp >= 0) trackOnce('demo-stage-view', { stage: STAGES[next].key });
    if (count === INVENTORY_ORDER.length) trackOnce('demo-full-build');
  });

  const prefersReducedMotion = useReducedMotion();

  useEffect(() => {
    const section = sectionRef.current;
    const video = videoRef.current;
    if (!section || !video || prefersReducedMotion) return;

    // 첫 화면에는 데모 윗부분이 포스터로만 걸쳐 보인다. 화면 위쪽 절반까지 올라와야 영상을 받고 튼다.
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isVideoPausedRef.current) {
          video.play().catch(() => video.pause());
        } else {
          video.pause();
        }
      },
      { rootMargin: '0px 0px -50% 0px' },
    );
    observer.observe(section);
    return () => observer.disconnect();
  }, [prefersReducedMotion]);

  // RailSegment 가 memo 라서 매번 새 함수를 넘기면 소용이 없다. ref 만 쓰므로 한 번만 만든다.
  const scrollToStage = useCallback((index: number) => {
    const el = sectionRef.current;
    if (!el) return;
    const top = el.getBoundingClientRect().top + window.scrollY;
    const scrollable = el.offsetHeight - window.innerHeight;
    const p = EXPAND_END + (STAGES[index].start + 0.03) * (1 - EXPAND_END);
    window.scrollTo({ top: top + p * scrollable, behavior: 'smooth' });
  }, []);

  const toggleVideo = () => {
    const video = videoRef.current;
    if (!video) return;
    const next = !isVideoPaused;
    isVideoPausedRef.current = next;
    setIsVideoPaused(next);
    if (next) video.pause();
    else video.play().catch(() => video.pause());
  };

  // 데모 화면이 펼쳐지기 전에는 버튼들이 투명하다. 키보드로 그 버튼에 닿으면 데모를 펼쳐 보이게 한다.
  const revealOnFocus = () => {
    if (progress.get() < EXPAND_END * 1.2) scrollToStage(stage);
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
        {/* 화면에선 영상 위에 겹쳐 보이지만, 읽는 순서는 섹션 맨 앞이어야 h2 → h3 차례가 맞는다. */}
        <motion.h2
          id='demo-heading'
          style={{ opacity: titleOpacity, y: titleY }}
          className='pointer-events-none absolute inset-0 z-10 flex items-center justify-center px-6 text-center text-4xl leading-tight font-bold tracking-[-0.03em] text-white [text-shadow:0_2px_24px_rgb(0_0_0/0.6)] sm:text-6xl'
        >
          <span>
            {text.demo.heading[0]}
            <br />
            {text.demo.heading[1]}
          </span>
        </motion.h2>
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
            preload='none'
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

          <motion.div
            style={{ opacity: uiOpacity }}
            onFocus={revealOnFocus}
            className='absolute inset-0'
          >
            {!prefersReducedMotion && (
              <button
                type='button'
                onClick={toggleVideo}
                aria-label={isVideoPaused ? text.demo.playVideo : text.demo.pauseVideo}
                data-umami-event='demo-video-toggle'
                className='absolute top-20 right-[4%] grid size-9 cursor-pointer place-items-center rounded-full bg-black/50 text-white/80 ring-1 ring-white/15 transition-colors hover:bg-black/70 hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'
              >
                <svg
                  width='12'
                  height='12'
                  viewBox='0 0 12 12'
                  aria-hidden='true'
                  fill='currentColor'
                >
                  {isVideoPaused ? (
                    <path d='M3 1.5v9l7.5-4.5z' />
                  ) : (
                    <path d='M2.5 1.5h2.5v9H2.5zM7 1.5h2.5v9H7z' />
                  )}
                </svg>
              </button>
            )}
            <AnimatePresence>
              {hasEntered && !isFull && (
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
                    purchased={inventoryCount}
                    {...recommendationsAfter(inventoryCount)}
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
              <nav aria-label={text.demo.stepsLabel} className='mt-5 flex gap-3'>
                {STAGES.map((s, i) => (
                  <RailSegment
                    key={s.key}
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
      </div>
    </section>
  );
}
