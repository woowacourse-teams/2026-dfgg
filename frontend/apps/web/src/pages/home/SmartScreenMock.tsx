// SmartScreen 경고창 목업 파일
import { AnimatePresence, motion } from 'motion/react';
import { useState } from 'react';

import { strings } from '../../i18n/i18n';

export default function SmartScreenMock() {
  const [step, setStep] = useState<'blocked' | 'details' | 'done'>('blocked');
  const isExpanded = step !== 'blocked';
  const s = strings();

  return (
    <figure className='w-full max-w-md'>
      <div className='bg-[#0063b1] p-6 text-white shadow-[0_30px_80px_-30px_rgb(0_99_177/0.6)] sm:p-8'>
        <p className='text-xl font-semibold sm:text-2xl'>{s.smartScreen.title}</p>
        <p className='mt-4 text-sm leading-relaxed text-white/90'>{s.smartScreen.body}</p>

        <AnimatePresence initial={false} mode='wait'>
          {isExpanded ? (
            <motion.dl
              key='details'
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              className='mt-4 grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-sm text-white/90'
            >
              <dt>{s.smartScreen.app}</dt>
              <dd>dfgg-setup.exe</dd>
              <dt>{s.smartScreen.publisher}</dt>
              <dd>{s.smartScreen.unknownPublisher}</dd>
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
              {s.smartScreen.moreInfo}
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
              {s.smartScreen.run}
              {step === 'details' && <Pulse />}
            </button>
          )}
          <span className='border border-white/60 px-6 py-1.5 text-sm'>
            {s.smartScreen.dontRun}
          </span>
        </div>
      </div>
      <figcaption className='mt-3 flex gap-3 text-sm text-ink-3'>
        {step === 'blocked' && s.smartScreen.stepMoreInfo}
        {step === 'details' && s.smartScreen.stepRun}
        {step === 'done' && (
          <>
            <span className='text-win'>{s.smartScreen.done}</span>
            <button
              type='button'
              data-umami-event='smartscreen-replay'
              onClick={() => setStep('blocked')}
              className='cursor-pointer underline-offset-4 hover:text-ink hover:underline'
            >
              {s.smartScreen.replay}
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
