import { useEffect } from 'react';

/**
 * 메인 프로세스에서 발생한 이벤트를 umami.track 으로 넘긴다.
 *
 * 메인 프로세스는 window.umami 에 접근할 수 없으므로(Node 환경) IPC로
 * 이벤트를 흘려보내고, 렌더러(브라우저 컨텍스트)인 여기서 실제로 기록한다.
 * 메인 창에서만 마운트한다 — 오버레이 창에는 umami 스크립트를 안 붙였다.
 */
export function useAnalyticsBridge() {
  useEffect(() => {
    // 창이 뜬 시점, umami 스크립트가 로드돼 있어야 잡힌다. Umami 세션 집계 기준으로
    // 앱을 몇 번 켰는지 보는 용도라 마운트당 한 번이면 된다.
    window.umami?.track('desktop-app-launch');

    const api = window.lcu;
    if (!api) return;
    // 메인 프로세스(electron/analytics.ts)가 이미 desktop- 접두사를 붙여 보낸다.
    return api.onAnalyticsEvent((event, data) => {
      window.umami?.track(event, data);
    });
  }, []);
}
