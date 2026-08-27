/**
 * 백엔드 호출을 메인 프로세스에서 한다.
 *
 * 렌더러가 직접 부르면 패키징된 앱에서 막힌다. file:// 로 뜨는 창은 Origin 이
 * null 이라 서버 CORS 허용이 필요하고, HTML 의 CSP connect-src 도 열어야 한다.
 * 메인 프로세스의 fetch 는 Node 쪽이라 둘 다 적용받지 않는다.
 */
const DEFAULT_API_BASE = 'https://dfgg.pro';

/** 로컬 백엔드로 붙이려면 DFGG_API_BASE 를 지정한다. */
const API_BASE = process.env.DFGG_API_BASE ?? DEFAULT_API_BASE;

const TIMEOUT_MS = 10_000;

async function postRecommendation(path: string, body: unknown): Promise<unknown> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });

  if (!response.ok) throw new Error(`${path} ${response.status}`);
  return response.json();
}

export function requestRecommendation(body: unknown): Promise<unknown> {
  return postRecommendation('/api/recommendations/v2', body);
}

/** v3는 구매한 아이템 목록을 담아 살 때마다 다시 부르는 방식이다. */
export function requestRecommendationV3(body: unknown): Promise<unknown> {
  return postRecommendation('/api/recommendations/v3', body);
}
