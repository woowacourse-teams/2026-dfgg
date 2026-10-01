import './FeedbackModal.css';

import * as Sentry from '@sentry/electron/renderer';
import { useEffect, useState } from 'react';

import type { EndedGame, FeedbackRating } from '../../../../shared/types';

interface FeedbackModalProps {
  game: EndedGame;
  onClose: () => void;
}

const OPTIONS: { rating: FeedbackRating; label: string }[] = [
  { rating: 'good', label: '좋음' },
  { rating: 'soso', label: '보통' },
  { rating: 'bad', label: '나쁨' },
];

// 감사 문구를 보여준 뒤 닫히기까지의 시간
const CLOSE_AFTER_THANKS_MS = 1500;

type Status = 'idle' | 'sending' | 'done' | 'error';

export default function FeedbackModal({ game, onClose }: FeedbackModalProps) {
  const [status, setStatus] = useState<Status>('idle');

  useEffect(() => {
    if (status !== 'done') return;
    const timer = setTimeout(onClose, CLOSE_AFTER_THANKS_MS);
    return () => clearTimeout(timer);
  }, [status, onClose]);

  const submit = async (rating: FeedbackRating) => {
    setStatus('sending');
    try {
      await window.feedback.submit(rating, game);
      setStatus('done');
    } catch (error) {
      setStatus('error');
      Sentry.captureException(error);
    }
  };

  return (
    <div className='feedback-modal' role='dialog' aria-labelledby='feedback-title'>
      <button type='button' className='feedback-close' aria-label='닫기' onClick={onClose}>
        ×
      </button>

      {status === 'done' ? (
        <p id='feedback-title' className='feedback-title'>
          소중한 의견 감사합니다!
        </p>
      ) : (
        <>
          <p id='feedback-title' className='feedback-title'>
            이번 게임의 아이템 추천은 어땠나요?
          </p>

          <div className='feedback-options'>
            {OPTIONS.map(({ rating, label }) => (
              <button
                key={rating}
                type='button'
                className='feedback-option'
                disabled={status === 'sending'}
                onClick={() => submit(rating)}
              >
                {label}
              </button>
            ))}
          </div>

          {status === 'error' && (
            <p className='feedback-error'>전송하지 못했어요. 다시 눌러 주세요.</p>
          )}
        </>
      )}
    </div>
  );
}
