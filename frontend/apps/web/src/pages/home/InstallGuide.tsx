// 설치 안내 파일
import { useRef } from 'react';

import useTrackInView from '../../hooks/useTrackInView';
import { strings } from '../../i18n/i18n';
import { MS_STORE_URL, track } from '../../lib';
import SmartScreenMock from './SmartScreenMock';

const text = strings().install;

export default function InstallGuide() {
  const sectionRef = useRef<HTMLElement>(null);
  useTrackInView(sectionRef, 'install-guide-view');

  return (
    <section
      ref={sectionRef}
      id='install'
      aria-labelledby='install-heading'
      className='mx-auto max-w-300 scroll-mt-20 border-t border-line px-4 pt-20 pb-8 sm:px-8 sm:py-28'
    >
      <div className='grid gap-12 lg:grid-cols-[1fr_1.1fr] lg:gap-20'>
        <div>
          <h2
            id='install-heading'
            className='text-3xl font-bold tracking-[-0.02em] whitespace-pre-line sm:text-4xl'
          >
            {text.title}
          </h2>
          <p className='mt-3 text-xl font-bold text-rank-1'>{text.highlight}</p>
          <p className='mt-4 leading-relaxed text-pretty text-ink-2'>
            {text.body.map((line) => (
              <span key={line} className='block'>
                {line}
              </span>
            ))}
            <span className='block lg:hidden'>{text.followMobile}</span>
            <span className='hidden lg:block'>{text.followDesktop}</span>
          </p>
          <p className='mt-6 max-w-[42ch] text-sm leading-relaxed text-ink-2'>
            {text.store.prefix}
            <a
              data-umami-event='store-click-guide'
              href={MS_STORE_URL}
              target='_blank'
              rel='noreferrer'
              className='font-medium text-ink underline underline-offset-4 hover:text-white'
            >
              {text.store.link}
            </a>
            {text.store.suffix}
          </p>
        </div>
        <SmartScreenMock />
      </div>

      <div className='mt-24'>
        <h3 className='text-sm text-ink-3'>{text.faqTitle}</h3>
        <ul className='mt-3 border-t border-line'>
          {text.faq.map((item) => (
            <li key={item.q} className='border-b border-line'>
              <details
                className='group'
                onToggle={(e) => {
                  if (e.currentTarget.open) track('faq-open', { question: item.q });
                }}
              >
                <summary className='flex cursor-pointer list-none items-center justify-between gap-6 py-5 font-bold transition-colors hover:text-white focus-visible:outline-2 focus-visible:outline-accent [&::-webkit-details-marker]:hidden'>
                  {item.q}
                  <span
                    aria-hidden='true'
                    className='text-ink-3 transition-transform duration-200 group-open:rotate-45'
                  >
                    +
                  </span>
                </summary>
                <p className='pb-5 leading-relaxed text-ink-2'>
                  {item.a.split(/(?<=[.)])\s/).map((sentence) => (
                    <span key={sentence} className='block'>
                      {sentence}
                    </span>
                  ))}
                </p>
              </details>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
