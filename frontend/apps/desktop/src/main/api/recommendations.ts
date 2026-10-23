import type { RecommendationResponse } from '../../shared/types';
import { RecommendationBody } from '../live/payload';
import { recordRecommendation } from '../analytics/gameTracker';
import { apiErrorKind, apiPost } from './client';

// 추천 계산이 5초를 넘기는 경우가 있어서 다른 요청보다 길게 기다린다.
const RECOMMENDATION_TIMEOUT_MS = 60_000;

export async function fetchItemRecommendations(body: RecommendationBody) {
  const startedAt = Date.now();
  try {
    const result = await apiPost<RecommendationResponse>(
      '/api/recommendations/v4',
      body,
      RECOMMENDATION_TIMEOUT_MS,
    );
    recordRecommendation({ ok: true, ms: Date.now() - startedAt, empty: !result.recommendedItems?.length });
    return result;
  } catch (error) {
    // Sentry 는 apiPost 가 'api-post /api/recommendations/v4' 로 이미 보낸다.
    recordRecommendation({ ok: false, ms: Date.now() - startedAt, kind: apiErrorKind(error) });
    throw error;
  }
}
