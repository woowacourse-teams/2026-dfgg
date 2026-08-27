import { requestRecommendationV3 } from '../../../packages/shared/api';
import type {
  RecommendationV3Request,
  RecommendationV3Response,
} from '../../../packages/shared/types';

/**
 * 2번 추천 방식(v3). requestBuild.ts와 같은 이유로 메인 프로세스를 거친다 —
 * 패키징된 앱의 렌더러(file://)는 CORS·CSP에 막힌다.
 */
export function requestBuildV3(
  request: RecommendationV3Request,
  signal?: AbortSignal,
): Promise<RecommendationV3Response> {
  const viaMain = window.lcu?.recommendV3;
  if (viaMain) return viaMain<RecommendationV3Response>(request);
  return requestRecommendationV3(request, signal);
}
