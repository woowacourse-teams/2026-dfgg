import type { RecommendationResponse } from '../../shared/types';
import { RecommendationBody } from '../live/payload';
import { recordRecommendation } from '../analytics/gameTracker';
import { apiPost } from './client';

export async function fetchItemRecommendations(body: RecommendationBody) {
  try {
    const result = await apiPost<RecommendationResponse>('/api/recommendations/v3', body);
    recordRecommendation(true);
    return result;
  } catch (error) {
    recordRecommendation(false);
    throw error;
  }
}
