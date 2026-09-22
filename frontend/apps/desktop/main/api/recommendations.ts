import type { RecommendationResponse } from '../../types';
import { RecommendationBody } from '../live/payload';
import { apiPost } from './client';

export async function fetchItemRecommendations(body: RecommendationBody) {
  return await apiPost<RecommendationResponse>('/api/recommendations/v3', body);
}
