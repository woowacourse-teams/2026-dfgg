import type {
  RecommendationRequest,
  RecommendationResponse,
  RecommendationV3Request,
  RecommendationV3Response,
} from './types';

/**
 * 개발 중에는 webpack devServer가 /recommendations 를 :8080 으로 프록시한다.
 * 패키징한 앱은 file:// 에서 뜨므로 상대 경로가 통하지 않는다 — 그때는
 * API_BASE 를 절대 주소로 바꾸고 백엔드 CORS에 그 오리진을 허용해야 한다.
 */
const API_BASE = '';

export async function requestRecommendation(
  request: RecommendationRequest,
  signal?: AbortSignal,
): Promise<RecommendationResponse> {
  const response = await fetch(`${API_BASE}/api/recommendations/v2`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) throw new Error(`/api/recommendations/v2 ${response.status}`);
  return response.json();
}

export async function requestRecommendationV3(
  request: RecommendationV3Request,
  signal?: AbortSignal,
): Promise<RecommendationV3Response> {
  const response = await fetch(`${API_BASE}/api/recommendations/v3`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    signal,
  });

  if (!response.ok) throw new Error(`/api/recommendations/v3 ${response.status}`);
  return response.json();
}
