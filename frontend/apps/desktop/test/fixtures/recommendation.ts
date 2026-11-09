import type { RecommendedItem } from '../../src/shared/types';

/** 오버레이가 그리는 추천 아이템 하나. 화면이 description 까지 읽어서 칸을 다 채운다 */
export function recommendedItem(overrides: Partial<RecommendedItem> = {}): RecommendedItem {
  return {
    id: 3031,
    name: '무한의 대검',
    imageUrl: '',
    description: { counter: [], ally: [], traits: [] },
    purchaseBundle: null,
    ...overrides,
  };
}
