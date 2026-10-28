import { describe, it, beforeAll, afterAll, expect } from 'vitest';
import { FakeDDragon, startFakeDDragon } from '../../../../test/fake-league/ddragonServer';
import { CHAMPIONS, ITEMS } from '../../../../test/fixtures/ddragon';
import { setTestEnv } from '../../testEnv';
import { getDDragonData } from '../ddragon';

describe('getDDragonData', () => {
  let ddragon: FakeDDragon;
  let data: Awaited<ReturnType<typeof getDDragonData>>;

  beforeAll(async () => {
    ddragon = await startFakeDDragon({ version: '15.20.1', champions: CHAMPIONS, items: ITEMS });
    setTestEnv({ ddragonUrl: ddragon.url });
    data = await getDDragonData();
  });

  afterAll(async () => {
    setTestEnv(null);
    await ddragon.close();
  });

  it('Boots 태그가 있는 아이템을 신발로 분류한다', async () => {
    expect(data.bootsItemIds.has(1001)).toBe(true);
  });
  it('Boots 태그가 없어도 재료가 신발이면 신발로 분류한다', async () => {
    expect(data.bootsItemIds.has(3172)).toBe(true);
  });
  it('상위 신발이 있어도 완성 신발은 하위 아이템으로 치지 않는다', () => {
    expect(data.componentItemIds.has(3006)).toBe(false);
  });
  it('상위 아이템이 있으면 하위 아이템, 없으면 완성 아이템으로 분류한다', async () => {
    expect(data.componentItemIds.has(1036)).toBe(true);
    expect(data.componentItemIds.has(3031)).toBe(false);
  });
  it('1000골드 이하 라인 시작 아이템을 하위 아이템에 포함한다', async () => {
    expect(data.componentItemIds.has(1055)).toBe(true);
  });
  it('챔피언 id 로 영문 이름을 찾는다', async () => {
    expect(data.championNames).toMatchObject({ Garen: 'Garen', MonkeyKing: 'Wukong' });
  });
  it('여러 번 불러도 DDragon 에는 한 번만 묻는다', async () => {
    await getDDragonData();
    await getDDragonData();

    expect(ddragon.requests()).toHaveLength(3);
  });
});
