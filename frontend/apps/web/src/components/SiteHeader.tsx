// 상단 헤더 파일
import { useMotionValueEvent, useScroll } from 'motion/react';
import { useState } from 'react';

import Logo from '../assets/icon.png';

export default function SiteHeader() {
  const { scrollY } = useScroll();
  const [isScrolled, setIsScrolled] = useState(false);

  useMotionValueEvent(scrollY, 'change', (y) => setIsScrolled(y > 8));

  return (
    <header
      className={`fixed inset-x-0 top-0 z-50 transition-colors duration-300 ${
        isScrolled ? 'bg-ground/80 backdrop-blur-md' : ''
      }`}
    >
      <nav
        aria-label='주요 메뉴'
        className='mx-auto flex h-16 max-w-300 items-center justify-between px-4 sm:px-8'
      >
        <a href='/' className='flex items-center gap-2' aria-label='DFGG 홈'>
          <img src={Logo} alt='' width={28} height={28} className='size-7' />
          <span className='font-display text-lg font-bold'>DFGG</span>
          <span className='text-xs font-medium text-ink-3'>Beta</span>
        </a>
        <a href='#install' className='text-sm text-ink-2 transition-colors hover:text-ink'>
          설치 안내
        </a>
      </nav>
    </header>
  );
}
