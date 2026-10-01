// 상단 헤더 파일
import { useMotionValueEvent, useScroll } from 'motion/react';
import { useState } from 'react';

import Logo from '../assets/icon.png';
import { LOCALE, localePath, strings } from '../i18n/i18n';

const text = strings().header;
const OTHER_LOCALE = LOCALE === 'ko' ? 'en' : 'ko';

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
        aria-label={text.navLabel}
        className='mx-auto flex h-16 max-w-300 items-center justify-between px-4 sm:px-8'
      >
        <a
          href={localePath(LOCALE)}
          data-umami-event='header-logo-click'
          className='flex items-center gap-2'
          aria-label={text.homeLabel}
        >
          <img src={Logo} alt='' width={28} height={28} className='size-7' />
          <span className='font-display text-lg font-bold'>DFGG</span>
          <span className='text-xs font-medium text-ink-3'>Beta</span>
        </a>
        <div className='flex items-center gap-5'>
          <a
            href='#install'
            data-umami-event='header-install-guide-click'
            className='text-sm text-ink-2 transition-colors hover:text-ink'
          >
            {text.installGuide}
          </a>
          <a
            href={localePath(OTHER_LOCALE)}
            hrefLang={OTHER_LOCALE}
            lang={OTHER_LOCALE}
            data-umami-event='header-language-switch'
            data-umami-event-to={OTHER_LOCALE}
            className='rounded px-2 py-1 text-xs font-medium text-ink-3 ring-1 ring-line transition-colors hover:text-ink'
          >
            {text.switchLanguage}
          </a>
        </div>
      </nav>
    </header>
  );
}
