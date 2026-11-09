// 챔피언 173개 응답 시간 점 그림. 고치기 전과 지금을 같은 눈금으로 나란히 둔다.
import { motion } from 'motion/react';

import { strings } from '../../i18n/i18n';

const text = strings().features.tech.speed;

const CHAMPIONS = 173;
/** 고치기 전에도 1초 안에 답하던 챔피언 수 */
const FAST_BEFORE = 4;

const W = 260;
const H = 150;
const PAD = 10;
// 0.1초 ~ 20초를 로그 눈금으로. 1초 선이 가운데쯤 온다
const MIN_S = 0.1;
const MAX_S = 20;
const y = (seconds: number) =>
  PAD + (1 - Math.log(seconds / MIN_S) / Math.log(MAX_S / MIN_S)) * (H - PAD * 2);
const LINE_Y = y(1);

/** 같은 그림이 매번 나오도록 고정된 의사 난수 */
function seeded(seed: number) {
  let s = seed;
  return () => {
    s = (s * 1103515245 + 12345) % 2147483648;
    return s / 2147483648;
  };
}

/**
 * 점 위치는 그림을 위한 대략적인 모양이다. 개수(173개 중 169개 / 0개)와 1초 선만 실제 값이다.
 * 고치기 전은 중앙값 약 3초, 가장 느린 챔피언 약 16초. 지금은 중앙값 약 0.3초.
 */
function spread(
  fastCount: number,
  slowRange: [number, number],
  fastRange: [number, number],
  seed: number,
) {
  const random = seeded(seed);
  return Array.from({ length: CHAMPIONS }, (_, index) => {
    const [lo, hi] = index < fastCount ? fastRange : slowRange;
    // 로그 공간에서 고르게 뿌린 뒤 가운데로 조금 모은다
    const t = (random() + random()) / 2;
    const seconds = lo * (hi / lo) ** t;
    return { x: PAD + random() * (W - PAD * 2), y: y(seconds), fast: seconds < 1 };
  });
}

const BEFORE = spread(FAST_BEFORE, [1.2, 16], [0.4, 0.95], 7);
const AFTER = spread(CHAMPIONS, [1, 1], [0.12, 0.9], 11);

function Panel({ dots, label, count }: { dots: typeof BEFORE; label: string; count: string }) {
  return (
    <figure className='flex-1'>
      <figcaption className='flex flex-col text-xs sm:flex-row sm:items-baseline sm:justify-between'>
        <span className='font-bold text-white'>{label}</span>
        <span className='text-ink-3'>{count}</span>
      </figcaption>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className='mt-2 w-full'
        role='img'
        aria-label={`${label}: ${count}`}
      >
        <rect x='0' y='0' width={W} height={H} rx='10' className='fill-surface' />
        {dots.map((dot, index) => (
          <motion.circle
            key={index}
            cx={dot.x}
            cy={dot.y}
            r='2.4'
            className={dot.fast ? 'fill-win' : 'fill-accent'}
            initial={{ opacity: 0 }}
            whileInView={{ opacity: 0.9 }}
            viewport={{ once: true }}
            transition={{ duration: 0.4, delay: (index % 40) * 0.012 }}
          />
        ))}
        <line x1='0' x2={W} y1={LINE_Y} y2={LINE_Y} className='stroke-white' strokeWidth='1.5' />
        <text
          x={W - 6}
          y={LINE_Y - 5}
          textAnchor='end'
          className='fill-white text-[10px] font-bold'
        >
          {text.line}
        </text>
      </svg>
    </figure>
  );
}

export default function SpeedDots() {
  return (
    <div className='flex gap-4'>
      <Panel dots={BEFORE} label={text.before} count={text.beforeCount} />
      <Panel dots={AFTER} label={text.after} count={text.afterCount} />
    </div>
  );
}
