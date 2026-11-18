import './ConfirmModal.css';

import { useEffect, useRef } from 'react';

type Props = {
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
};

/** 되돌릴 수 없는 설정 변경 전에 한 번 더 묻는 모달 */
export default function ConfirmModal({
  title,
  message,
  confirmLabel,
  cancelLabel,
  onConfirm,
  onCancel,
}: Props) {
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    // 실수로 Enter를 눌러 삭제되지 않도록 취소에 포커스를 둔다
    cancelRef.current?.focus();

    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onCancel]);

  return (
    <div className='confirm-backdrop' onClick={onCancel}>
      <div
        className='confirm-modal'
        role='alertdialog'
        aria-modal='true'
        aria-labelledby='confirm-title'
        aria-describedby='confirm-message'
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id='confirm-title' className='confirm-title'>
          {title}
        </h2>
        <p id='confirm-message' className='confirm-message'>
          {message}
        </p>
        <div className='confirm-actions'>
          <button ref={cancelRef} type='button' className='confirm-cancel' onClick={onCancel}>
            {cancelLabel}
          </button>
          <button type='button' className='confirm-ok' onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
