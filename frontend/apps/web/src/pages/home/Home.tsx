// 홈 페이지 파일
import { MotionConfig, useMotionValueEvent, useScroll } from 'motion/react';

import SiteFooter from '../../components/SiteFooter';
import SiteHeader from '../../components/SiteHeader';
import { trackOnce } from '../../lib';
import Hero from './Hero';
import InstallGuide from './InstallGuide';
import ScrollDemo from './ScrollDemo';

const SCROLL_DEPTHS = [25, 50, 75, 100];

export default function Home() {
  const { scrollYProgress } = useScroll();

  useMotionValueEvent(scrollYProgress, 'change', (p) => {
    SCROLL_DEPTHS.filter((depth) => p * 100 >= depth - 1).forEach((depth) =>
      trackOnce('scroll-depth', { percent: depth }),
    );
  });

  return (
    <MotionConfig reducedMotion='user'>
      <SiteHeader />
      <main>
        <Hero />
        <ScrollDemo />
        <InstallGuide />
      </main>
      <div className='mx-auto max-w-300 px-4 py-12 sm:px-8'>
        <SiteFooter />
      </div>
    </MotionConfig>
  );
}
