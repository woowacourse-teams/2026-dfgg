// 기능 소개: 한 판의 흐름을 따라 실제 앱 화면을 지그재그로 놓는다
import { motion } from 'motion/react';
import { useRef } from 'react';

import useTrackInView from '../../hooks/useTrackInView';
import { strings } from '../../i18n/i18n';

const text = strings().features;

type Step = (typeof text.steps)[number];

/** 캡처는 2배 크기로 떠 두었다. width·height 는 원본 크기로 적어 비율만 잡는다. */
type Shot = { src: string; width: number; height: number };

const SHOTS: Record<string, { main: Shot; sub?: Shot; wide?: boolean }> = {
  'champ-select': {
    main: { src: '/media/features/champselect.webp', width: 1000, height: 1434 },
    sub: { src: '/media/features/runes.webp', width: 1000, height: 600 },
  },
  'in-game': {
    main: { src: '/media/features/overlay.webp', width: 2000, height: 1153 },
    wide: true,
  },
  'post-game': {
    main: { src: '/media/features/postgame.webp', width: 1000, height: 355 },
    sub: { src: '/media/features/ranking.webp', width: 1000, height: 1228 },
  },
  history: {
    main: { src: '/media/features/home.webp', width: 1000, height: 1560 },
    sub: { src: '/media/features/detail.webp', width: 1000, height: 1222 },
  },
};

const reveal = {
  initial: { opacity: 0, y: 24 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, amount: 0.25 },
  transition: { duration: 0.7, ease: [0.16, 1, 0.3, 1] as const },
};

/** 앱 창 하나. 데스크톱 창처럼 테두리와 그림자를 둔다. */
function Window({ shot, alt, className = '' }: { shot: Shot; alt: string; className?: string }) {
  return (
    <img
      src={shot.src}
      alt={alt}
      width={shot.width}
      height={shot.height}
      loading='lazy'
      decoding='async'
      className={`h-auto rounded-2xl bg-surface ring-1 ring-line shadow-[0_30px_80px_-30px_rgb(0_0_0/0.9)] ${className}`}
    />
  );
}

/**
 * 화면 묶음. 큰 창 하나에 작은 창을 비스듬히 겹쳐 깊이를 낸다.
 * 게임 화면(오버레이)은 가로로 넓어서 혼자 크게 둔다.
 */
function Shots({ step }: { step: Step }) {
  const { main, sub, wide } = SHOTS[step.key];

  if (wide) {
    return (
      <div className='relative'>
        <Window shot={main} alt={step.imageAlt} className='w-full' />
      </div>
    );
  }

  // 위아래로 긴 창(밴픽·홈)은 오른쪽 아래에, 가로로 긴 카드(방금 끝난 판)는 그 아래 오른쪽에 겹친다.
  // 카드의 점수 글자를 가리지 않도록 카드 오른쪽(KDA 쪽)만 살짝 덮는다.
  const tall = main.height > main.width;
  return (
    <div className={`relative mx-auto ${tall ? 'max-w-110 pb-16 sm:pb-20' : 'max-w-140 pb-[52%]'}`}>
      <Window shot={main} alt={step.imageAlt} className={tall ? 'w-[78%]' : 'w-[88%]'} />
      {sub && 'subImageAlt' in step && (
        <motion.div
          initial={{ opacity: 0, x: 24, rotate: 0 }}
          whileInView={{ opacity: 1, x: 0, rotate: 2 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.8, delay: 0.15, ease: [0.16, 1, 0.3, 1] }}
          className={`absolute right-0 bottom-0 ${tall ? 'w-[62%]' : 'w-[46%]'}`}
        >
          <Window shot={sub} alt={step.subImageAlt ?? ''} className='w-full' />
        </motion.div>
      )}
    </div>
  );
}

function Row({ step, index }: { step: Step; index: number }) {
  const ref = useRef<HTMLElement>(null);
  useTrackInView(ref, `features-${step.key}-view`);
  const flipped = index % 2 === 1;
  const wide = SHOTS[step.key].wide;

  return (
    <section
      ref={ref}
      id={step.key}
      aria-labelledby={`${step.key}-title`}
      className={`grid scroll-mt-24 items-center gap-10 md:gap-16 ${
        // 게임 화면은 가로로 넓어서 그림 칸을 더 넓게. 좌우가 바뀌면 비율도 같이 바꾼다
        wide ? (flipped ? 'md:grid-cols-[3fr_2fr]' : 'md:grid-cols-[2fr_3fr]') : 'md:grid-cols-2'
      }`}
    >
      <motion.div {...reveal} className={flipped ? 'md:order-2' : ''}>
        {/* 중계 그래픽의 코너 딱지처럼 기울인 단계 표시 */}
        <span className='inline-flex -skew-x-12 text-xs font-extrabold tracking-wide'>
          <b className='bg-accent-strong px-2 py-0.5 text-white'>
            {String(index + 1).padStart(2, '0')}
          </b>
          <span className='bg-white/10 px-2 py-0.5 text-ink'>{step.label}</span>
        </span>
        <h2
          id={`${step.key}-title`}
          className='mt-5 text-[clamp(1.6rem,3.2vw,2.4rem)] leading-tight font-bold tracking-[-0.02em] text-white'
        >
          {step.title}
        </h2>
        <p className='mt-4 max-w-120 text-base leading-relaxed text-ink-2'>{step.body}</p>
        <dl className='mt-7 max-w-120 divide-y divide-line border-y border-line'>
          {step.points.map((point) => (
            <div key={point.name} className='flex gap-4 py-3.5'>
              <dt className='w-24 flex-none text-sm font-bold text-white sm:w-28'>{point.name}</dt>
              <dd className='text-sm leading-relaxed text-ink-2'>{point.text}</dd>
            </div>
          ))}
        </dl>
      </motion.div>

      <motion.div {...reveal} className={`relative ${flipped ? 'md:order-1' : ''}`}>
        {/* 화면 뒤에 번지는 붉은 빛. 바탕이 검정이라 창이 떠 보이게 한다 */}
        <div
          aria-hidden='true'
          className='absolute inset-[10%] -z-10 rounded-full bg-accent-strong/25 blur-[90px]'
        />
        <Shots step={step} />
      </motion.div>
    </section>
  );
}

export default function Showcase() {
  return (
    <div className='mx-auto flex max-w-300 flex-col gap-28 px-4 sm:gap-36 sm:px-8'>
      {text.steps.map((step, index) => (
        <Row key={step.key} step={step} index={index} />
      ))}
    </div>
  );
}
