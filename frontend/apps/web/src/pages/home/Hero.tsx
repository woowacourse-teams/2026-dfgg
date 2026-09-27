// 히어로 파일
import { motion } from 'motion/react';
import type { ReactNode } from 'react';

import DownloadButtons from '../../components/DownloadButtons';
import BlurText from '../../components/effects/BlurText';
import LightRays from '../../components/effects/LightRays';
import ChatLine from './ChatLine';

const fadeUp = (delay: number) => ({
  initial: { opacity: 0, y: 12 },
  animate: { opacity: 1, y: 0 },
  transition: { duration: 0.6, delay, ease: [0.16, 1, 0.3, 1] as const },
});

function Underline({ children, delay }: { children: ReactNode; delay: number }) {
  return (
    <span className='relative isolate inline-block px-0.5 font-semibold text-white'>
      {children}
      <span
        aria-hidden='true'
        className='absolute -inset-x-1 bottom-[0.08em] -z-10 h-[0.5em] -rotate-2 -skew-x-12'
      >
        <motion.span
          initial={{ scaleX: 0 }}
          animate={{ scaleX: 1 }}
          transition={{ duration: 0.45, delay, ease: [0.65, 0, 0.35, 1] }}
          className='block size-full origin-left rounded-[2px_6px_3px_8px] bg-accent/75'
        />
      </span>
    </span>
  );
}

export default function Hero() {
  return (
    <section
      aria-labelledby='hero-heading'
      className='relative flex min-h-[62svh] flex-col items-center justify-center px-4 pt-24 pb-4 text-center sm:pt-28 sm:pb-10'
    >
      <LightRays />
      <ChatLine
        history={{
          time: '12:46',
          name: '탑블레이드가렌',
          champion: '레넥톤',
          text: '원딜 템 뭐갈거?',
        }}
        typed={{ time: '12:47', name: '원딜장인', champion: '징크스', text: '템 뭐 가야됨?' }}
      />
      <h1
        id='hero-heading'
        className='relative mt-6 font-impact text-[clamp(2rem,9.4vw,6rem)] leading-[1.1] tracking-[-0.01em] whitespace-nowrap'
      >
        <span className='sr-only'>“템 뭐 가야됨?” </span>
        <BlurText lines={['이젠 물어보지 마세요']} delay={120} />
      </h1>
      <motion.p
        {...fadeUp(1.1)}
        className='relative mt-6 text-lg leading-relaxed font-medium tracking-[-0.01em] text-white/80 sm:text-2xl'
      >
        DFGG가 <Underline delay={1.5}>양 팀 조합</Underline>과{' '}
        <Underline delay={1.75}>실시간 상황</Underline>에 맞춰
        <br />
        <strong className='font-bold text-white'>코어템</strong>을 추천해줘요!
      </motion.p>
      <motion.div {...fadeUp(1.25)} className='relative mt-9'>
        <DownloadButtons />
        <p className='mt-3 text-sm text-ink-3'>Windows 10·11</p>
      </motion.div>
    </section>
  );
}
