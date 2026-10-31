import type { DDragonItem } from '../../src/main/live/types';

/** DDragon 아이템 하나. 분류에 쓰는 칸(tags, from, into, gold)만 신경 쓰면 된다. */
export function ddragonItem(overrides: Partial<DDragonItem> = {}): DDragonItem {
  return {
    name: '',
    description: '',
    gold: { base: 0, total: 0, sell: 0, purchasable: true },
    tags: [],
    maps: {},
    ...overrides,
  };
}

/** 분류 규칙을 확인하기 좋은 아이템 몇 개. 키는 아이템 id */
export const ITEMS: Record<string, DDragonItem> = {
  // 신발
  '1001': ddragonItem({
    name: '장화',
    tags: ['Boots'],
    into: ['3006'],
    gold: { base: 300, total: 300, sell: 210, purchasable: true },
  }),
  '3006': ddragonItem({ name: '광전사의 군화', tags: ['Boots'], from: ['1001'], into: ['3172'] }),
  '3172': ddragonItem({ name: '건메탈 군화', tags: [], from: ['3006'] }), // 3단계 신발: Boots 태그 없음
  // 하위 아이템과 완성 아이템
  '1036': ddragonItem({
    name: '롱소드',
    tags: ['Damage'],
    into: ['3031'],
    gold: { base: 350, total: 350, sell: 245, purchasable: true },
  }),
  '3031': ddragonItem({ name: '무한의 대검', tags: ['Damage'], from: ['1036'] }),
  // 시작 아이템
  '1055': ddragonItem({
    name: '도란의 검',
    tags: ['Lane'],
    gold: { base: 450, total: 450, sell: 180, purchasable: true },
  }),
};

export const CHAMPIONS = [
  { id: 'Garen', name: 'Garen' },
  { id: 'MonkeyKing', name: 'Wukong' },
];
