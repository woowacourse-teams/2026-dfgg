import type { PostHog } from 'posthog-js';

// 실제 서비스 도메인에서만 보내기
const ENABLED = ['dfgg.pro', 'www.dfgg.pro'].includes(location.hostname);

/**
 * posthog-js 는 첫 화면에 필요 없는데 번들의 큰 몫을 차지한다.
 * 페이지 로딩이 끝난 뒤 따로 받아 오고, 그 전에 생긴 이벤트는 받아 온 뒤 보낸다.
 */
let posthogReady: Promise<PostHog> | null = null;

/** 첫 화면에 필요 없는 스크립트를 첫 화면 받기와 겹치지 않게 미룰 때 쓴다. */
export const afterPageLoad = () =>
  new Promise<void>((resolve) => {
    if (document.readyState === 'complete') resolve();
    else window.addEventListener('load', () => resolve(), { once: true });
  });

async function loadPosthog() {
  await afterPageLoad();
  const { default: posthog } = await import(/* webpackChunkName: "posthog" */ 'posthog-js');
  posthog.init('phc_xCWU3NRFsUo8ebg8T25PFRSuu75Q9HQzdcqDNXpxHUir', {
    api_host: 'https://us.i.posthog.com',
    capture_pageview: 'history_change', // 페이지 이동마다
    autocapture: false, // 우마미처럼 정한 이벤트만
    person_profiles: 'identified_only',
  });
  posthog.register({ platform: 'web' });
  return posthog;
}

export function initAnalytics() {
  if (!ENABLED) return;
  posthogReady = loadPosthog();
  posthogReady.catch(() => {
    // 광고 차단기 등으로 못 받으면 통계만 빠진다. 페이지는 그대로 쓸 수 있어야 한다.
  });

  // data-umami-event 클릭은 Umami 스크립트가 Umami 로 보내므로, 여기서는 PostHog 에만 보낸다.
  // data-umami-event-os="windows" 같은 추가 속성도 { os: 'windows' } 로 같이 넘긴다.
  document.addEventListener('click', (e) => {
    const el = (e.target as Element).closest<HTMLElement>('[data-umami-event]');
    if (!el) return;
    const props: Record<string, string> = {};
    for (const [key, value] of Object.entries(el.dataset)) {
      if (key.startsWith('umamiEvent') && key !== 'umamiEvent') {
        const name = key.slice('umamiEvent'.length);
        props[name[0].toLowerCase() + name.slice(1)] = value ?? '';
      }
    }
    capture(el.dataset.umamiEvent!, props);
  });
}

export function capture(name: string, props?: Record<string, unknown>) {
  posthogReady?.then((posthog) => posthog.capture(name, props)).catch(() => {});
}
