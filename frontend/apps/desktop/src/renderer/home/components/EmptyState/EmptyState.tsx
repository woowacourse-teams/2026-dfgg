import './EmptyState.css';

import * as Sentry from '@sentry/electron/renderer';
import { useState } from 'react';

import type { LcuStatus } from '../../../../shared/types';

// 소환사 정보가 없는 이유를 상태별로 구분해 알린다.
// 클라이언트가 꺼져 있는데 '불러오는 중' 이라고 하면 오해를 준다.
const TITLE: Record<LcuStatus, string> = {
  disconnected: '롤 클라이언트를 실행해 주세요',
  connecting: '클라이언트에 연결하는 중',
  connected: '소환사 정보를 불러오는 중',
};

/** 버튼을 누른 뒤 클라이언트 창이 뜰 때까지 다시 못 누르게 막아 두는 시간 */
const LAUNCH_COOLDOWN_MS = 8000;

type LaunchState = 'idle' | 'launching' | 'failed';

/** 롤 클라이언트와 아직 연결되지 않았을 때의 화면 */
function EmptyState({ status }: { status: LcuStatus | null }) {
  const [launch, setLaunch] = useState<LaunchState>('idle');
  const current = status ?? 'disconnected';

  const launchClient = async () => {
    setLaunch('launching');
    window.analytics.track('client-launch');

    try {
      const started = await window.lcu.launchClient();
      if (!started) return setLaunch('failed');

      // 클라이언트가 뜨면 연결 상태가 바뀌면서 이 화면이 사라진다. 안 뜬 경우에만 버튼을 되살린다.
      setTimeout(() => setLaunch('idle'), LAUNCH_COOLDOWN_MS);
    } catch (error) {
      Sentry.captureException(error);
      setLaunch('failed');
    }
  };

  return (
    <main className='empty'>
      <div className='empty-mark'>
        <img src='./icon.png' alt='' />
      </div>

      <p className='empty-title'>{TITLE[current]}</p>

      {current === 'disconnected' && (
        <>
          <button
            type='button'
            className='empty-launch'
            disabled={launch === 'launching'}
            onClick={launchClient}
          >
            {launch === 'launching' ? '클라이언트 여는 중...' : '클라이언트 실행'}
          </button>

          {launch === 'failed' && (
            <p className='empty-error' role='alert'>
              롤 설치 위치를 찾지 못했어요. 직접 실행해 주세요.
            </p>
          )}
        </>
      )}
    </main>
  );
}

export default EmptyState;
