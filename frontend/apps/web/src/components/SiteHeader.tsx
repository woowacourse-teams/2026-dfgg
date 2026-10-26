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
    <>
      {/* 키보드로 들어오면 첫 Tab 에 보인다. 머리말 링크를 건너뛰고 본문으로 간다. */}
      <a
        href='#main'
        className='sr-only rounded-md bg-accent-strong px-4 py-2 font-bold text-white focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-60'
      >
        {text.skipToContent}
      </a>
      <header
        className={`fixed inset-x-0 top-0 z-50 transition-colors duration-300 ${
          isScrolled ? 'bg-ground/80 backdrop-blur-md' : ''
        }`}
      >
        <nav
          aria-label={text.navLabel}
          className='mx-auto flex h-16 max-w-300 items-center justify-between px-4 sm:px-8'
        >
          {/* aria-label 로 덮으면 보이는 글자(DFGG Beta)와 읽는 이름이 달라진다. 보이는 글자 뒤에 '홈'만 덧붙인다. */}
          <a
            href={localePath(LOCALE)}
            data-umami-event='header-logo-click'
            className='flex items-center gap-2'
          >
            <img src={Logo} alt='' width={28} height={28} className='size-7' />
            {/* 띄어쓰기가 없으면 'DFGGBeta홈'으로 붙어 읽힌다. flex 라 화면 간격은 그대로다. */}
            <span className='font-display text-lg font-bold'>DFGG</span>{' '}
            <span className='text-xs font-medium text-ink-3'>Beta</span>{' '}
            <span className='sr-only'>{text.homeLabel}</span>
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
              aria-label={text.switchLanguageLabel}
              data-umami-event='header-language-switch'
              data-umami-event-to={OTHER_LOCALE}
              className='rounded px-2 py-1 text-xs font-medium text-ink-3 ring-1 ring-line transition-colors hover:text-ink'
            >
              {text.switchLanguage}
            </a>
          </div>
        </nav>
      </header>
    </>
  );
}
