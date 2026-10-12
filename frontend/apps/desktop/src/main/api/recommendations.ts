import type { RecommendationResponse } from '../../shared/types';
import { RecommendationBody } from '../live/payload';
import { recordRecommendation } from '../analytics/gameTracker';
import { apiPost } from './client';

// 추천 계산이 5초를 넘기는 경우가 있어서 다른 요청보다 길게 기다린다.
const RECOMMENDATION_TIMEOUT_MS = 60_000;

export async function fetchItemRecommendations(body: RecommendationBody) {
  try {
    const result = await apiPost<RecommendationResponse>(
      '/api/recommendations/v3',
      body,
      RECOMMENDATION_TIMEOUT_MS,
    );
    recordRecommendation(true);
    return result;
  } catch (error) {
    recordRecommendation(false);
    throw error;
  }
}
