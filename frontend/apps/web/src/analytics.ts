import posthog from 'posthog-js';

// 실제 서비스 도메인에서만 보내기
const ENABLED = ['dfgg.pro', 'www.dfgg.pro'].includes(location.hostname);

export function initAnalytics() {
  if (!ENABLED) return;
  posthog.init('phc_xCWU3NRFsUo8ebg8T25PFRSuu75Q9HQzdcqDNXpxHUir', {
    api_host: 'https://us.i.posthog.com',
    capture_pageview: 'history_change', // 페이지 이동마다
    autocapture: false, // 우마미처럼 정한 이벤트만
    person_profiles: 'identified_only',
  });
  posthog.register({ platform: 'web' });

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
  if (ENABLED) posthog.capture(name, props);
}
