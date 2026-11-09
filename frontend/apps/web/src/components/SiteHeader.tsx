// 상단 헤더 파일
import { useMotionValueEvent, useScroll } from 'motion/react';
import { useState } from 'react';

import Logo from '../assets/icon.png';
import { LOCALE, localePath, strings, stripLocale } from '../i18n/i18n';

const text = strings().header;
const OTHER_LOCALE = LOCALE === 'ko' ? 'en' : 'ko';
// 언어를 바꿔도 보던 페이지에 머문다. 홈이면 '/' 그대로
const CURRENT_PATH = stripLocale(window.location.pathname);
const ON_FEATURES = CURRENT_PATH.startsWith('/features');

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
          <div className='flex items-center gap-4 sm:gap-5'>
            <a
              href={localePath(LOCALE, '/features')}
              aria-current={ON_FEATURES ? 'page' : undefined}
              data-umami-event='header-features-click'
              className={`text-sm transition-colors hover:text-ink ${
                ON_FEATURES ? 'text-white' : 'text-ink-2'
              }`}
            >
              {text.features}
            </a>
            {/* 설치 안내는 홈에만 있다 */}
            {!ON_FEATURES && (
              <a
                href='#install'
                data-umami-event='header-install-guide-click'
                className='hidden text-sm text-ink-2 transition-colors hover:text-ink sm:inline'
              >
                {text.installGuide}
              </a>
            )}
            {/* 지금 페이지의 다운로드 자리로 내려간다 (홈은 히어로, 기능 소개는 맨 아래) */}
            <a
              href='#download'
              data-umami-event='header-download-click'
              data-umami-event-page={ON_FEATURES ? 'features' : 'home'}
              className='rounded-md bg-accent-strong px-3.5 py-1.5 text-sm font-bold text-white transition-colors hover:bg-accent focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent active:bg-accent-deep'
            >
              {text.download}
            </a>
            <a
              href={localePath(OTHER_LOCALE, CURRENT_PATH)}
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
