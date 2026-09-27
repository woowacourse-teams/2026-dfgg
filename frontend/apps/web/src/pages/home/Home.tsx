// 홈 페이지 파일
import { MotionConfig } from 'motion/react';

import SiteFooter from '../../components/SiteFooter';
import SiteHeader from '../../components/SiteHeader';
import Hero from './Hero';
import InstallGuide from './InstallGuide';
import ScrollDemo from './ScrollDemo';

export default function Home() {
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
