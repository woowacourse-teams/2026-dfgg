import { useEffect, useState } from 'react';

import { DESKTOP_TEXT } from '../../../packages/i18n/desktop';
import { useDict } from '../../../packages/i18n/useLang';
import type { UpdateState } from '../electron/updater';

const IDLE: UpdateState = { status: 'idle', version: null, percent: 0 };

/**
 * 업데이트 상태 줄.
 *
 * 다 받아도 스스로 재시작하지 않는다. 밴픽 중에 앱이 꺼지면 그게 더 큰 사고라,
 * 준비됐다는 사실만 알리고 언제 적용할지는 사용자가 고른다.
 */
export default function UpdateBanner() {
  const [state, setState] = useState<UpdateState>(IDLE);
  const t = useDict(DESKTOP_TEXT).update;
  const api = window.lcu?.update;

  useEffect(() => {
    if (!api) return;
    void api.getState().then(setState);
    return api.onState(setState);
  }, [api]);

  // 조용할 때와 실패했을 때는 아무것도 그리지 않는다. 실패는 사용자가 할 수 있는 게 없다.
  if (!api || state.status === 'idle' || state.status === 'error') return null;

  if (state.status === 'downloading') {
    return (
      <p className='mt-4 rounded border border-neutral-800 bg-neutral-900/60 p-3 text-xs text-neutral-400'>
        {t.downloading(state.version, state.percent)}
      </p>
    );
  }

  return (
    <div className='mt-4 flex flex-wrap items-center justify-between gap-3 rounded border border-emerald-500/40 bg-emerald-500/10 p-3'>
      <p className='text-sm text-emerald-300'>
        {t.readyBefore}
        <strong>{state.version}</strong>
        {t.readyAfter}
      </p>
      <button
        type='button'
        onClick={() => void api.install()}
        className='shrink-0 cursor-pointer rounded bg-emerald-500 px-3 py-1.5 text-xs font-bold text-emerald-950'
      >
        {t.restart}
      </button>
    </div>
  );
}
