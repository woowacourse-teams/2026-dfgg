import type { BrowserWindow } from 'electron';

/**
 * 메인 프로세스는 window.umami 를 못 쓴다(Node 환경). 여기서는 렌더러로
 * 이벤트를 실어 보내기만 하고, 실제 umami.track 호출은 렌더러의
 * useAnalyticsBridge 가 한다.
 *
 * 오버레이 창에는 umami 스크립트를 안 붙였으므로 메인 창에만 보낸다.
 * 메인 창이 없으면(닫힌 동안) 그 이벤트는 유실된다 — 재시도하지 않는다.
 *
 * website ID를 웹(dfgg.pro)과 공유하므로(Umami 무료 플랜은 사이트 1개) 이름 앞에
 * desktop- 을 붙여 대시보드에서 웹 이벤트와 섞이지 않게 한다.
 */
export function trackEvent(
  getMainWindow: () => BrowserWindow | null,
  event: string,
  data?: Record<string, unknown>,
) {
  const window = getMainWindow();
  if (!window || window.isDestroyed()) return;
  window.webContents.send('analytics:event', `desktop-${event}`, data);
}
