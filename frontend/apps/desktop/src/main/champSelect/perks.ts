import type { Lockfile, RecommendedRunePage, RuneApplyError, TodayPick } from '../../shared/types';
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

/** 프로 룬은 트리나 핵심 룬이 비어 올 수 있다. 그럴 땐 넣을 수 없어 null */
export function fromPro({ runes }: TodayPick): PerkSelection | null {
  if (!runes.primaryTree || !runes.secondaryTree || !runes.keystone) return null;
  return {
    primaryStyleId: runes.primaryTree.id,
    subStyleId: runes.secondaryTree.id,
    selectedPerkIds: [runes.keystone, ...runes.primary, ...runes.secondary, ...runes.shards].map(
      ({ id }) => id,
    ),
  };
}

/**
 * DFGG 페이지를 지우고 새로 만들어 지금 페이지로 둔다. PUT 은 클라이언트 버전에 따라 무시될 때가 있어 지우고 만든다.
 * DFGG 페이지가 없고 빈 칸도 없으면 사용자 페이지를 지우지 않고 no-slot 을 돌려준다.
 */
export async function applyPerkPage(
  lockfile: Lockfile,
  selection: PerkSelection,
): Promise<RuneApplyError | null> {
  if (selection.selectedPerkIds.length !== PERK_COUNT) return 'failed';

  try {
    const pages = (await lcuRequest<LcuPerkPage[]>(lockfile, '/lol-perks/v1/pages')) ?? [];
    const ours = pages.find((page) => page.name === DFGG_PAGE_NAME && page.isDeletable);

    if (ours) {
      await lcuRequest(lockfile, `/lol-perks/v1/pages/${ours.id}`, { method: 'DELETE' });
    } else {
      const inventory = await lcuRequest<LcuPerkInventory>(lockfile, '/lol-perks/v1/inventory');
      const used = pages.filter((page) => page.isDeletable).length;
      if (inventory && used >= inventory.ownedPageCount) return 'no-slot';
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
