import { useEffect, useState } from 'react';

import type { Lineup } from '../electron/types';

export type LcuStatus = 'connected' | 'disconnected' | 'unavailable';

/**
 * 메인 프로세스가 밀어주는 밴픽 현황을 구독한다.
 * window.lcu 는 preload가 붙여준다 — 브라우저로 열면 없으므로 unavailable로 둔다.
 */
/** 롤 창 모드. 전체 화면(0)에서는 어떤 오버레이도 게임 위에 올라가지 못한다. */
export const WINDOW_MODE_FULLSCREEN = 0;

export function useLineup() {
  const [lineup, setLineup] = useState<Lineup | null>(null);
  const [windowMode, setWindowMode] = useState<number | null>(null);
  // effect 안에서 동기적으로 setState하면 렌더가 한 번 더 도므로 초기값에서 판단한다.
  const [status, setStatus] = useState<LcuStatus>(() =>
    window.lcu ? 'disconnected' : 'unavailable',
  );

  useEffect(() => {
    const api = window.lcu;
    if (!api) return;

    // 창이 늦게 떴을 때를 대비해 현재 상태를 한 번 당겨온다.
    void api.getLineup().then(setLineup);

    const offLineup = api.onLineup(setLineup);
    const offStatus = api.onStatus(setStatus);
    const offWindowMode = api.onWindowMode(setWindowMode);

    return () => {
      offLineup();
      offStatus();
      offWindowMode();
    };
  }, []);

  return { lineup, status, windowMode };
}
