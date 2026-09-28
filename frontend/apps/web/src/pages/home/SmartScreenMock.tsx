// SmartScreen 경고창 목업 파일
import { AnimatePresence, motion } from 'motion/react';
import { useState } from 'react';

export default function SmartScreenMock() {
  const [step, setStep] = useState<'blocked' | 'details' | 'done'>('blocked');
  const isExpanded = step !== 'blocked';

  return (
    <figure className='w-full max-w-md'>
      <div className='bg-[#0063b1] p-6 text-white shadow-[0_30px_80px_-30px_rgb(0_99_177/0.6)] sm:p-8'>
        <p className='text-xl font-semibold sm:text-2xl'>Windows의 PC 보호</p>
        <p className='mt-4 text-sm leading-relaxed text-white/90'>
          Microsoft Defender SmartScreen에서 인식할 수 없는 앱의 시작을 차단했습니다. 이 앱을
          실행하면 PC가 위험에 노출될 수 있습니다.
        </p>

        <AnimatePresence initial={false} mode='wait'>
          {isExpanded ? (
            <motion.dl
              key='details'
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              className='mt-4 grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm text-white/90'
            >
              <dt>앱:</dt>
              <dd>dfgg-setup.exe</dd>
              <dt>게시자:</dt>
              <dd>알 수 없는 게시자</dd>
            </motion.dl>
          ) : (
            <motion.button
              key='more'
              type='button'
              data-umami-event='smartscreen-more-info'
              onClick={() => setStep('details')}
              exit={{ opacity: 0 }}
              className='relative mt-4 cursor-pointer text-sm underline underline-offset-2'
            >
              추가 정보
              <Pulse />
            </motion.button>
          )}
        </AnimatePresence>

        <div className='mt-10 flex justify-end gap-2'>
          {isExpanded && (
            <button
              type='button'
              data-umami-event='smartscreen-run'
              onClick={() => setStep('done')}
              className='relative cursor-pointer border border-white bg-white/10 px-6 py-1.5 text-sm'
            >
              실행
              {step === 'details' && <Pulse />}
            </button>
          )}
          <span className='border border-white/60 px-6 py-1.5 text-sm'>실행 안 함</span>
        </div>
      </div>
      <figcaption className='mt-3 flex gap-3 text-sm text-ink-3'>
        {step === 'blocked' && '1. 추가 정보를 눌러보세요'}
        {step === 'details' && '2. 실행을 누르면 설치가 시작됩니다'}
        {step === 'done' && (
          <>
            <span className='text-win'>설치가 시작됩니다</span>
            <button
              type='button'
              data-umami-event='smartscreen-replay'
              onClick={() => setStep('blocked')}
              className='cursor-pointer underline-offset-4 hover:text-ink hover:underline'
            >
              다시 보기
            </button>
          </>
        )}
      </figcaption>
    </figure>
  );
}

function Pulse() {
  return (
    <motion.span
      aria-hidden='true'
      className='pointer-events-none absolute -inset-2 border-2 border-rank-1'
      animate={{ opacity: [0.9, 0], scale: [1, 1.15] }}
      transition={{ duration: 1.3, repeat: Infinity, ease: 'easeOut' }}
    />
  );
}
