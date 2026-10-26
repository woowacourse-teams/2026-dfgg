import type { Lockfile, RecommendedRunePage, RuneApplyError, TodayPick } from '../../shared/types';
import { runeTreeOf } from '../../shared/runeTrees';
import { lcuRequest } from '../lcu/client';
import type { LcuPerkInventory, LcuPerkPage } from '../lcu/champSelect.types';
import { reportError } from '../sentry';

/** 우리가 만들고 덮어쓰는 룬 페이지 이름. 사용자가 만든 다른 페이지는 건드리지 않는다. */
export const DFGG_PAGE_NAME = 'DFGG';

/** 핵심 룬 1 + 주 계열 3 + 보조 계열 2 + 파편 3 */
const PERK_COUNT = 9;

export interface PerkSelection {
  primaryStyleId: number;
  subStyleId: number;
  selectedPerkIds: number[];
}

export function fromRecommended(page: RecommendedRunePage): PerkSelection {
  return {
    primaryStyleId: page.primaryStyle.id,
    subStyleId: page.subStyle.id,
    selectedPerkIds: [
      page.keystone,
      ...page.primaryRunes,
      ...page.subRunes,
      ...page.statShards,
    ].map(({ id }) => id),
  };
}

/**
 * 프로 룬. 응답에 트리가 비어 오면 핵심 룬과 보조 룬으로 트리를 찾는다.
 * 핵심 룬이 없거나 트리를 못 찾으면 넣을 수 없어 null
 */
export function fromPro({ runes }: TodayPick): PerkSelection | null {
  if (!runes.keystone) return null;
  const primaryStyleId = runes.primaryTree?.id ?? runeTreeOf(runes.keystone.id);
  const subStyleId = runes.secondaryTree?.id ?? runeTreeOf(runes.secondary[0]?.id ?? 0);
  if (!primaryStyleId || !subStyleId) return null;
  return {
    primaryStyleId,
    subStyleId,
    selectedPerkIds: [runes.keystone, ...runes.primary, ...runes.secondary, ...runes.shards].map(
      ({ id }) => id,
    ),
  };
}

/**
 * DFGG 페이지를 지우고 새로 만들어 지금 페이지로 둔다. PUT 은 클라이언트 버전에 따라 무시될 때가 있어 지우고 만든다.
 * DFGG 페이지가 없고 칸도 꽉 찼으면 지금 고른 페이지(없으면 맨 앞 페이지)를 지우고 그 자리에 만든다.
 * 지울 수 있는 페이지가 하나도 없으면(기본 페이지만 있을 때) no-slot.
 */
export async function applyPerkPage(
  lockfile: Lockfile,
  selection: PerkSelection,
): Promise<RuneApplyError | null> {
  if (selection.selectedPerkIds.length !== PERK_COUNT) return 'failed';

  try {
    const pages = (await lcuRequest<LcuPerkPage[]>(lockfile, '/lol-perks/v1/pages')) ?? [];
    const ours = pages.find((page) => page.name === DFGG_PAGE_NAME && page.isDeletable);

    const deletable = pages.filter((page) => page.isDeletable);
    let replace = ours;

    if (!replace) {
      const inventory = await lcuRequest<LcuPerkInventory>(lockfile, '/lol-perks/v1/inventory');
      const full = inventory !== null && deletable.length >= inventory.ownedPageCount;
      if (full) {
        replace = deletable.find((page) => page.current) ?? deletable[0];
        if (!replace) return 'no-slot';
      }
    }

    if (replace) {
      await lcuRequest(lockfile, `/lol-perks/v1/pages/${replace.id}`, { method: 'DELETE' });
    }

    await lcuRequest(lockfile, '/lol-perks/v1/pages', {
      method: 'POST',
      body: { name: DFGG_PAGE_NAME, ...selection, current: true },
    });
    return null;
  } catch (error) {
    console.error('룬 페이지 적용 실패', error);
    reportError('perk-apply', error);
    return 'failed';
  }
}
