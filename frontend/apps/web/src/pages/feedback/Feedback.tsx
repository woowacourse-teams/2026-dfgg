import { type SubmitEvent, useState } from 'react';

import { useDict } from '../../../../../packages/i18n/useLang';
import { FEEDBACK_TEXT } from '../../../../../packages/i18n/web';
import ErrorMessage from '../../components/ErrorMessage';

const MAX_LENGTH = 1000;

export default function Feedback() {
  const [message, setMessage] = useState('');
  const [error, setError] = useState<string>('');
  const t = useDict(FEEDBACK_TEXT);

  const trimmed = message.trim();
  const canSubmit = trimmed.length > 0;

  const handleSubmit = async (event: SubmitEvent) => {
    event.preventDefault();
    if (!canSubmit) return;
    setError('');

    const date = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });

    try {
      const response = await fetch('/feedback', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          date: `${date}`,
          content: message,
        }),
      });

      if (!response.ok) setError(t.failed);
    } catch (error) {
      console.error('feedback submit failed', error);
      setError(t.failed);
    }
  };

  return (
    <>
      <h1 className='font-display text-3xl font-bold sm:text-4xl'>{t.title}</h1>
      <p className='mt-3 leading-relaxed text-ink-2'>{t.intro}</p>

      <form onSubmit={handleSubmit} className='mt-8'>
        <label htmlFor='feedback' className='sr-only'>
          {t.label}
        </label>
        <textarea
          id='feedback'
          value={message}
          onChange={(event) => setMessage(event.target.value.slice(0, MAX_LENGTH))}
          rows={9}
          placeholder={t.placeholder}
          className='chamfer-sm w-full resize-y bg-surface-2 p-4 leading-relaxed text-ink shadow-[inset_0_0_0_1px_var(--color-line)] transition-shadow outline-none placeholder:text-ink-3 focus:shadow-[inset_0_0_0_2px_var(--color-accent)]'
        />

        <div className='mt-3 flex flex-wrap items-center justify-between gap-3'>
          <p className='text-xs text-ink-3' aria-live='polite'>
            {trimmed.length} / {MAX_LENGTH}
          </p>

          <button
            data-umami-event='feedback-submit'
            type='submit'
            disabled={!canSubmit}
            className='chamfer-sm cursor-pointer bg-accent-strong px-6 py-3 font-display text-sm font-bold tracking-[0.12em] text-white uppercase transition-colors hover:bg-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:bg-surface-2 disabled:text-ink-3'
          >
            {t.submit}
          </button>
        </div>
      </form>

      {error && <ErrorMessage className='mt-4'>{error}</ErrorMessage>}
    </>
  );
}
