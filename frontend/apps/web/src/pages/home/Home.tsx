import { useNavigate } from 'react-router-dom';

import { useDict } from '../../../../../packages/i18n/useLang';
import { HOME_TEXT } from '../../../../../packages/i18n/web';
import Intro1 from '../../assets/intro1.png';
import Intro2 from '../../assets/intro2.jpg';
import DesktopAppButton from '../../components/DesktopAppButton';

/** 문구는 사전에서, 이미지는 여기서. 순서가 곧 짝이다. */
const FEATURE_IMAGES = [Intro1, Intro2];

export default function Home() {
  const navigate = useNavigate();
  const t = useDict(HOME_TEXT);

  return (
    <>
      {/* 가운데 정렬은 히어로에만 준다. 공통 래퍼에 걸면 본문까지 따라간다. */}
      <div className='text-center'>
        <h1 className='font-display font-bold text-balance mb-12'>
          <span className='block text-xl leading-snug text-ink-2 sm:text-3xl'>{t.headlineTop}</span>
          <span className='mt-2 block text-4xl leading-[1.1] sm:text-6xl'>
            <em className='bg-linear-to-br from-red-500 via-red-400 to-red-950 bg-clip-text text-transparent text-accent not-italic'>
              {t.headlineAccent}
            </em>{' '}
            {t.headlineRest}
          </span>
        </h1>

        <div className='flex flex-row gap-24 items-center justify-center'>
          <DesktopAppButton data='desktop-app-home' className='px-10 py-4' />
          <button
            data-umami-event='10-champion-recommend-top'
            onClick={() => navigate('/champion-select')}
            type='button'
            className='px-10 py-4 cursor-pointer rounded-xl bg-linear-to-br from-cobalt-deep to-cobalt font-bold text-white transition-opacity hover:opacity-90'
          >
            {t.cta}
          </button>
        </div>

        <div aria-hidden='true' className='mx-auto mt-7 h-px w-14 bg-accent/50' />
      </div>

      <section className='mt-16 grid gap-x-10 gap-y-16 text-left sm:mt-24 lg:grid-cols-2'>
        {t.features.map((feature, index) => (
          <article key={feature.title}>
            <p className='flex items-center gap-2.5 text-sm font-medium text-ink-3'>
              <span aria-hidden='true' className='h-4 w-1 bg-accent' />
              {feature.eyebrow}
            </p>

            <h2 className='mt-3 font-display text-3xl leading-tight font-bold sm:text-4xl'>
              {feature.title}
            </h2>

            <div className='mt-6 space-y-1 leading-relaxed text-ink-2 text-pretty'>
              {feature.body.map((line) => (
                <p key={line}>{line}</p>
              ))}
            </div>

            <figure className='relative mt-6'>
              <img src={FEATURE_IMAGES[index]} alt={feature.alt} className='block w-full' />
              <div
                aria-hidden='true'
                className='pointer-events-none absolute inset-x-0 top-0 h-45 bg-linear-to-b from-ground via-ground/70 to-transparent'
              />
              <div
                aria-hidden='true'
                className='pointer-events-none absolute inset-x-0 bottom-0 h-16 bg-linear-to-t from-ground via-ground/70 to-transparent'
              />
            </figure>
          </article>
        ))}
      </section>

      <button
        data-umami-event='10-champion-recommend-bottom'
        type='button'
        onClick={() => navigate('/champion-select')}
        className='group mx-auto mt-16 flex w-fit cursor-pointer items-center gap-2 rounded-2xl border border-line bg-surface-2/60 px-8 py-3.5 font-bold text-ink transition-colors hover:border-accent-strong hover:bg-accent-strong hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'
      >
        {t.tryIt}
        <span aria-hidden='true' className='transition-transform group-hover:translate-x-1'>
          →
        </span>
      </button>
    </>
  );
}
