// 히어로 파일
import { motion } from 'motion/react';
import type { ReactNode } from 'react';

import DownloadButtons from '../../components/DownloadButtons';
import BlurText from '../../components/effects/BlurText';
import LightRays from '../../components/effects/LightRays';
import { strings } from '../../i18n/i18n';
import ChatLine from './ChatLine';

const text = strings().hero;
const sub = text.subtitle;

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
        history={{ time: '12:46', ...text.chatHistory }}
        typed={{ time: '12:47', ...text.chatTyped }}
      />
      <h1
        id='hero-heading'
        className='relative mt-6 font-impact text-[clamp(2rem,9.4vw,6rem)] leading-[1.1] tracking-[-0.01em] whitespace-nowrap'
      >
        <span className='sr-only'>“{text.chatTyped.text}” </span>
        <BlurText lines={text.headline} delay={120} />
      </h1>
      <motion.p
        {...fadeUp(1.1)}
        className='relative mt-6 text-lg leading-relaxed font-medium tracking-[-0.01em] text-white/80 sm:text-2xl'
      >
        {sub.prefix}
        <Underline delay={1.5}>{sub.teamComp}</Underline>
        {sub.joiner}
        <Underline delay={1.75}>{sub.liveState}</Underline>
        {sub.suffix}
        <br />
        {sub.secondLinePrefix}
        <strong className='font-bold text-white'>{sub.coreItem}</strong>
        {sub.secondLineSuffix}
      </motion.p>
      <motion.div {...fadeUp(1.25)} className='relative mt-9'>
        <DownloadButtons />
        <p className='mt-3 text-sm text-ink-3'>{strings().download.requirement}</p>
      </motion.div>
    </section>
  );
}
