// 기능 소개 페이지: 한 판의 흐름(밴픽 → 게임 중 → 게임 후 → 전적)과 추천을 믿을 수 있는 이유
import { motion, MotionConfig } from 'motion/react';
import { useEffect } from 'react';

import DownloadButtons from '../../components/DownloadButtons';
import SiteFooter from '../../components/SiteFooter';
import SiteHeader from '../../components/SiteHeader';
import { strings } from '../../i18n/i18n';
import Showcase from './Showcase';
import Tech from './Tech';

const t = strings();
const text = t.features;

const fadeUp = (delay: number) => ({
  initial: { opacity: 0, y: 14 },
  animate: { opacity: 1, y: 0 },
  transition: { duration: 0.7, delay, ease: [0.16, 1, 0.3, 1] as const },
});

/** 네 단계를 한 줄로. 누르면 그 단계로 내려간다 */
function StepNav() {
  return (
    <motion.nav {...fadeUp(0.35)} aria-label={text.stepsLabel} className='mt-12'>
      <ol className='mx-auto grid max-w-180 grid-cols-4'>
        {text.steps.map((step, index) => (
          <li key={step.key} className='relative'>
            {/* 단계 사이를 잇는 선 */}
            {index > 0 && (
              <span
                aria-hidden='true'
                className='absolute top-2 right-1/2 left-[-50%] h-px bg-gradient-to-r from-line to-accent/60'
              />
            )}
            <a
              href={`#${step.key}`}
              data-umami-event='features-step-click'
              data-umami-event-step={step.key}
              className='group relative flex flex-col items-center gap-2 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent'
            >
              <span className='size-4 rotate-45 border-2 border-accent bg-ground transition-colors group-hover:bg-accent' />
              <span className='text-xs text-ink-2 transition-colors group-hover:text-white sm:text-sm'>
                {step.label}
              </span>
            </a>
          </li>
        ))}
      </ol>
    </motion.nav>
  );
}

export default function Features() {
  useEffect(() => {
    document.title = text.title;
  }, []);

  return (
    <MotionConfig reducedMotion='user'>
      <SiteHeader />
      <main id='main' tabIndex={-1} className='features-page scroll-mt-16 outline-none'>
        <section
          aria-labelledby='features-heading'
          className='relative overflow-hidden px-4 pt-32 pb-20 text-center sm:pt-40 sm:pb-28'
        >
          {/* 위에서 내려오는 붉은 빛. 히어로의 광선보다 약하게 둔다 */}
          <div
            aria-hidden='true'
            className='absolute top-[-40%] left-1/2 -z-10 h-[80vh] w-[80vw] -translate-x-1/2 rounded-full bg-accent-strong/20 blur-[120px]'
          />
          <motion.p {...fadeUp(0)} className='text-sm font-bold text-accent'>
            {text.kicker}
          </motion.p>
          <motion.h1
            {...fadeUp(0.1)}
            id='features-heading'
            className='mt-4 text-[clamp(2.2rem,7vw,4.8rem)] leading-[1.06] font-extrabold tracking-[-0.03em] text-white'
          >
            {text.headline.map((line) => (
              <span key={line} className='block'>
                {line}
              </span>
            ))}
          </motion.h1>
          <motion.p
            {...fadeUp(0.2)}
            className='mx-auto mt-6 max-w-160 text-base leading-[1.8] text-ink-2 sm:text-lg sm:whitespace-pre-line'
          >
            {text.intro}
          </motion.p>
          <StepNav />
        </section>

        <Showcase />

        <div className='mt-36 sm:mt-48'>
          <Tech />
        </div>

        {/* 헤더의 다운로드 버튼이 내려오는 곳 */}
        <section
          id='download'
          aria-labelledby='cta-title'
          className='mx-auto mt-36 max-w-300 scroll-mt-24 px-4 sm:mt-48 sm:px-8'
        >
          <div className='relative overflow-hidden rounded-3xl bg-surface px-6 py-16 text-center ring-1 ring-line sm:py-20'>
            <div
              aria-hidden='true'
              className='absolute bottom-[-60%] left-1/2 h-[120%] w-[70%] -translate-x-1/2 rounded-full bg-accent-strong/30 blur-[100px]'
            />
            <h2
              id='cta-title'
              className='relative text-3xl font-extrabold tracking-[-0.02em] text-white sm:text-4xl'
            >
              {text.cta.title}
            </h2>
            <p className='relative mt-3 text-ink-2'>{text.cta.body}</p>
            <div className='relative mt-8 flex flex-col items-center'>
              <DownloadButtons />
              <p className='mt-3 text-sm text-ink-3'>{t.download.requirement}</p>
            </div>
          </div>
        </section>
      </main>
      <div className='mx-auto max-w-300 px-4 py-12 sm:px-8'>
        <SiteFooter />
      </div>
    </MotionConfig>
  );
}
