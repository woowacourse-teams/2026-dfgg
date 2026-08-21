import { useNavigate } from 'react-router-dom';

import Intro1 from '../../assets/intro1.png';
import Intro2 from '../../assets/intro2.jpg';
import DesktopAppButton from '../../components/DesktopAppButton';

const FEATURES = [
  {
    eyebrow: '데스크톱 앱',
    title: '조합 자동 인식',
    body: [
      '밴픽이 끝나면 롤 클라이언트에서 양 팀 조합을 그대로 읽어옵니다.',
      '열 명을 하나씩 입력할 필요가 없습니다.',
    ],
    image: Intro1,
    alt: '데스크톱 앱이 양 팀 조합을 읽어와 추천 아이템을 보여주는 화면',
    fade: true,
  },
  {
    eyebrow: '인게임 오버레이',
    title: '게임 위에 바로',
    body: [
      '추천 아이템 6개를 순서대로 게임 화면 위에 띄워줍니다.',
      '알트탭 없이 그 자리에서 확인하세요.',
    ],
    image: Intro2,
    alt: '게임 화면 위에 추천 아이템 6개가 떠 있는 오버레이',
    fade: true,
  },
];

export default function Home() {
  const navigate = useNavigate();

  return (
    <>
      {/* 가운데 정렬은 히어로에만 준다. 공통 래퍼에 걸면 본문까지 따라간다. */}
      <div className='text-center'>
        <h1 className='font-display font-bold text-balance mb-12'>
          <span className='block text-xl leading-snug text-ink-2 sm:text-3xl'>
            나의 조합, 상대 조합에 맞는
          </span>
          <span className='mt-2 block text-4xl leading-[1.1] sm:text-6xl'>
            <em className='bg-linear-to-br from-red-500 via-red-400 to-red-950 bg-clip-text text-transparent text-accent not-italic'>
              6가지
            </em>{' '}
            아이템 추천
          </span>
        </h1>

        <div className='flex flex-row gap-24 items-center justify-center'>
          <DesktopAppButton className='px-10 py-4' />
          <button
            onClick={() => navigate('/champion-select')}
            type='button'
            className='px-10 py-4 cursor-pointer rounded-xl bg-linear-to-br from-cobalt-deep to-cobalt font-bold text-white transition-opacity hover:opacity-90'
          >
            추천 받아보기
          </button>
        </div>

        <div aria-hidden='true' className='mx-auto mt-7 h-px w-14 bg-accent/50' />
      </div>

      <section className='mt-16 grid gap-x-10 gap-y-16 text-left sm:mt-24 lg:grid-cols-2'>
        {FEATURES.map((feature) => (
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

            <figure
              className={
                feature.fade
                  ? 'relative mt-6'
                  : 'chamfer mt-6 overflow-hidden bg-surface-2 shadow-[inset_0_0_0_1px_var(--color-line)]'
              }
            >
              <img src={feature.image} alt={feature.alt} className='block w-full' />
              {feature.fade && (
                <>
                  <div
                    aria-hidden='true'
                    className='pointer-events-none absolute inset-x-0 top-0 h-45 bg-linear-to-b from-ground via-ground/70 to-transparent'
                  />
                  <div
                    aria-hidden='true'
                    className='pointer-events-none absolute inset-x-0 bottom-0 h-16 bg-linear-to-t from-ground via-ground/70 to-transparent'
                  />
                </>
              )}
            </figure>
          </article>
        ))}
      </section>

      <button
        type='button'
        onClick={() => navigate('/champion-select')}
        className='group mx-auto mt-16 flex w-fit cursor-pointer items-center gap-2 rounded-2xl border border-line bg-surface-2/60 px-8 py-3.5 font-bold text-ink transition-colors hover:border-accent-strong hover:bg-accent-strong hover:text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'
      >
        체험해보기
        <span aria-hidden='true' className='transition-transform group-hover:translate-x-1'>
          →
        </span>
      </button>
    </>
  );
}
