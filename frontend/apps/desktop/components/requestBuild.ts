import { requestRecommendation } from '../../../packages/shared/api';
import type { RecommendationRequest, RecommendationResponse } from '../../../packages/shared/types';

/**
 * 데스크톱에서는 메인 프로세스를 거쳐 백엔드를 부른다.
 *
 * 패키징하면 렌더러가 file:// 에서 뜨는데, 그때 나가는 요청은 Origin 이 null 이라
 * 서버 CORS 와 HTML 의 CSP 양쪽에 막힌다. 메인 프로세스의 fetch 는 Node 라
 * 그 제약이 없다. 브라우저(웹)에서는 preload 가 없으므로 기존 경로를 그대로 쓴다.
 */
export function requestBuild(
  request: RecommendationRequest,
  signal?: AbortSignal,
): Promise<RecommendationResponse> {
  const viaMain = window.lcu?.recommend;
  if (viaMain) return viaMain<RecommendationResponse>(request);
  return requestRecommendation(request, signal);
}
