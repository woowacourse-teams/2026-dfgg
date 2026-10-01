import { type KeyboardEvent, useState } from 'react';

import slide01 from '../assets/dfgg-store-01.jpg';
import slide02 from '../assets/dfgg-store-02.jpg';
import slide03 from '../assets/dfgg-store-03.jpg';
import slide04 from '../assets/dfgg-store-04.jpg';
import slide05 from '../assets/dfgg-store-05.jpg';

/**
 * Microsoft Store 에 올린 소개 이미지. 설명 문구가 이미지 안에 들어 있어
 * 따로 캡션을 달지 않는다. 대신 같은 내용을 alt 로 옮겨 적어 스크린 리더와
 * 이미지가 안 뜨는 상황을 받쳐준다.
 */
const SLIDES = [
  {
    src: slide01,
    alt: '밴픽 연동 — 롤 클라이언트 옆에 붙어, 밴픽 화면의 조합을 자동으로 읽어옵니다',
  },
  {
    src: slide02,
    alt: '조합 분석 — 우리 팀과 상대 팀을 함께 보고, 지금 이 판에 맞는 템을 추천합니다',
  },
  {
    src: slide03,
    alt: '인게임 오버레이 — 게임이 시작되면 추천 템트리가 화면 위 오버레이로 따라붙습니다',
  },
  {
    src: slide04,
    alt: '단축키·크기 — Alt+D 한 번으로 켜고 끄고, 오버레이 크기는 네 단계로 조절합니다',
  },
  {
    src: slide05,
    alt: '자동 연결 — 클라이언트에 붙는 작은 창 하나, 설치하면 바로 연결됩니다',
  },
];

const ARROW =
  'absolute top-1/2 z-10 -translate-y-1/2 cursor-pointer rounded-full bg-ground/70 p-2.5 text-ink ' +
  'transition-colors hover:bg-accent-strong hover:text-white ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent';

export default function StoreSlides() {
  const [index, setIndex] = useState(0);

  // 양 끝에서 반대편으로 감는다. 장수가 적어 끝에서 막히면 답답하다.
  const move = (step: number) => setIndex((i) => (i + step + SLIDES.length) % SLIDES.length);

  const handleKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'ArrowLeft') move(-1);
    if (event.key === 'ArrowRight') move(1);
  };

  return (
    <div
      role='region'
      aria-roledescription='슬라이드'
      aria-label='데스크톱 앱 소개'
      tabIndex={0}
      onKeyDown={handleKeyDown}
      className='relative focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent'
    >
      <div className='chamfer overflow-hidden'>
        {/*
          한 줄로 늘어놓고 통째로 밀어 실제로 넘어가는 느낌을 준다.
          각 장이 shrink-0 + w-full 이라 칸 너비가 곧 한 장의 너비다.
        */}
        <div
          className='flex transition-transform duration-500 ease-out'
          style={{ transform: `translateX(-${index * 100}%)` }}
        >
          {SLIDES.map((slide, i) => (
            <img
              key={slide.src}
              src={slide.src}
              alt={slide.alt}
              width={1600}
              height={900}
              /* 첫 장만 즉시 받고 나머지는 미룬다. 넉 장이 800KB 다. */
              loading={i === 0 ? 'eager' : 'lazy'}
              className='block w-full shrink-0'
            />
          ))}
        </div>
      </div>

      <button
        type='button'
        onClick={() => move(-1)}
        aria-label='이전 슬라이드'
        className={`${ARROW} left-3`}
      >
        ←
      </button>
      <button
        type='button'
        onClick={() => move(1)}
        aria-label='다음 슬라이드'
        className={`${ARROW} right-3`}
      >
        →
      </button>

      <div className='mt-4 flex items-center justify-center gap-2'>
        {SLIDES.map((slide, i) => (
          <button
            key={slide.src}
            type='button'
            onClick={() => setIndex(i)}
            aria-label={`${i + 1}번째 슬라이드`}
            aria-current={i === index}
            className={`h-1.5 cursor-pointer rounded-full transition-all ${
              i === index ? 'w-6 bg-accent' : 'w-1.5 bg-line hover:bg-ink-3'
            }`}
          />
        ))}
      </div>

      {/* 화면에는 안 보이지만, 넘길 때마다 스크린 리더가 현재 장을 읽어준다. */}
      <p aria-live='polite' className='sr-only'>
        {index + 1} / {SLIDES.length} — {SLIDES[index].alt}
      </p>
    </div>
  );
}
