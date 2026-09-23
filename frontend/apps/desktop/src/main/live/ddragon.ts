import type { DDragonChampionList, DDragonItemList, DDragonVersions } from './types';

let cache: Promise<{
  version: string;
  championNames: Record<string, string>;
  componentItemIds: Set<number>;
}> | null = null;

async function fetchVersion(): Promise<DDragonVersions> {
  try {
    const response = await fetch('https://ddragon.leagueoflegends.com/api/versions.json');
    if (!response.ok) {
      throw new Error(`Ddragon version HTTP 에러! 상태 코드: ${response.status}`);
    }

    const data = (await response.json()) as DDragonVersions;
    return data;
  } catch {
    throw new Error('Ddragon version 요청 실패');
  }
}

async function fetchChampionList(version: string): Promise<DDragonChampionList> {
  try {
    const response = await fetch(
      `https://ddragon.leagueoflegends.com/cdn/${version}/data/en_US/champion.json`,
    );
    if (!response.ok) {
      throw new Error(`Ddragon champion HTTP 에러! 상태 코드: ${response.status}`);
    }

    const data = (await response.json()) as DDragonChampionList;
    return data;
  } catch {
    cache = null;
    throw new Error('Ddragon champion 요청 실패');
  }
}

// 아이템 정보 가져오기
async function fetchItemList(version: string): Promise<DDragonItemList> {
  try {
    const response = await fetch(
      `https://ddragon.leagueoflegends.com/cdn/${version}/data/en_US/item.json`,
    );
    if (!response.ok) {
      throw new Error(`Ddragon champion HTTP 에러! 상태 코드: ${response.status}`);
    }

    const data = (await response.json()) as DDragonItemList;
    return data;
  } catch {
    cache = null;
    throw new Error('Ddragon item 요청 실패');
  }
}

// 하위 아이템 제외하기
function toComponentItemIds(list: DDragonItemList): Set<number> {
  const ids = Object.entries(list.data)
    .filter(([, item]) => {
      const hasUpgrade = (item.into?.length ?? 0) > 0;
      const isUpgradedBoots = item.tags.includes('Boots') && (item.from?.length ?? 0) > 0;
      return hasUpgrade && !isUpgradedBoots;
    })
    .map(([id]) => Number(id));

  return new Set(ids);
}

function toStartingItemIds(list: DDragonItemList): Set<number> {
  const ids = Object.entries(list.data)
    .filter(([, item]) => {
      const isBase = (item.from?.length ?? 0) === 0 && (item.into?.length ?? 0) === 0;
      const isLaneOrJungle = item.tags.includes('Lane') || item.tags.includes('Jungle');
      const isCheap = item.gold.total > 0 && item.gold.total <= 1000;
      return isBase && isLaneOrJungle && isCheap;
    })
    .map(([id]) => Number(id));

  return new Set(ids);
}

// 챔피언, 아이템 정보 가져오기
async function load(): Promise<{
  version: string;
  championNames: Record<string, string>;
  componentItemIds: Set<number>;
}> {
  const version = await fetchVersion();

  const [championList, itemList] = await Promise.all([
    fetchChampionList(version[0]),
    fetchItemList(version[0]),
  ]);

  const componentItemIds = toComponentItemIds(itemList);
  const startingItemIds = toStartingItemIds(itemList);

  return {
    version: version[0],
    championNames: Object.fromEntries(
      Object.values(championList.data).map((champion) => [champion.id, champion.name]),
    ),
    componentItemIds: new Set([...componentItemIds, ...startingItemIds]),
  };
}

export async function getDDragonData() {
  if (cache === null) cache = load();
  return cache;
}
