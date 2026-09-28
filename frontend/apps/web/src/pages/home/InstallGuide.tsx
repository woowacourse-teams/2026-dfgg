// 설치 안내 파일
import { useRef } from 'react';

import useTrackInView from '../../hooks/useTrackInView';
import { MS_STORE_URL, track } from '../../lib';
import SmartScreenMock from './SmartScreenMock';

const FAQ = [
  {
    q: '오버레이가 안 보여요',
    a: '롤이 전체 화면이면 오버레이가 가려집니다. DFGG가 게임 밖에서 테두리 없음 모드로 바꿔두지만(원래 설정은 백업), 그 뒤에 다시 바꿨다면 설정 → 그래픽 → 창 모드를 테두리 없음으로 맞춰주세요.',
  },
  {
    q: '추천이 안 떠요',
    a: '게임이 시작돼야 뜹니다. 대기실이나 챔피언 선택 중에는 나오지 않고, 롤 클라이언트가 켜져 있어야 연결됩니다.',
  },
  {
    q: '다운로드가 막혀요',
    a: '브라우저 다운로드 목록에서 계속 또는 유지를 누르세요. 파일은 DFGG GitHub 릴리스에서 받습니다.',
  },
  {
    q: 'Mac에서도 되나요?',
    a: '지금은 Windows만 지원합니다.',
  },
];

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
          <h2 id='install-heading' className='text-3xl font-bold tracking-[-0.02em] sm:text-4xl'>
            파란 경고창이 떠도 괜찮습니다
          </h2>
          <p className='mt-3 text-xl font-bold text-rank-1'>아직 준비 중이라 뜨는 경고예요!</p>
          <p className='mt-4 leading-relaxed text-pretty text-ink-2'>
            exe로 받으면 Windows가 알 수 없는 게시자라며 한 번 막습니다.
            <br />
            코드 서명 인증서가 아직 없어서 그래요.
            <br />
            <span className='lg:hidden'>아래</span>
            <span className='hidden lg:inline'>옆</span> 창처럼 누르면 설치됩니다.
          </p>
          <p className='mt-6 max-w-[42ch] text-sm leading-relaxed text-ink-2'>
            경고 없이 설치하려면{' '}
            <a
              data-umami-event='store-click-guide'
              href={MS_STORE_URL}
              target='_blank'
              rel='noreferrer'
              className='font-medium text-ink underline underline-offset-4 hover:text-white'
            >
              Microsoft Store 버전
            </a>
            을 받으세요.
          </p>
        </div>
        <SmartScreenMock />
      </div>

      <div className='mt-24'>
        <h3 className='text-sm text-ink-3'>자주 묻는 질문</h3>
        <ul className='mt-3 border-t border-line'>
          {FAQ.map((item) => (
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
