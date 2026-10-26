import { describe, expect, it, vi } from 'vitest';

vi.mock('../sentry', () => ({ reportError: vi.fn() }));
vi.mock('../lcu/client', () => ({ lcuRequest: vi.fn() }));

import type { Lockfile, RecommendedRunePage, TodayPick } from '../../shared/types';
import { lcuRequest } from '../lcu/client';
import { applyPerkPage, DFGG_PAGE_NAME, fromPro, fromRecommended } from './perks';

const rune = (id: number) => ({ id, name: null, imageUrl: null });

const PAGE: RecommendedRunePage = {
  rank: 1,
  primaryStyle: rune(8200),
  keystone: rune(8229),
  primaryRunes: [rune(8226), rune(8210), rune(8237)],
  subStyle: rune(8300),
  subRunes: [rune(8304), rune(8345)],
  statShards: [rune(5008), rune(5008), rune(5011)],
  evidence: { games: 608, pickRate: 0.4 },
};

const SELECTION = fromRecommended(PAGE);
const LOCKFILE = { port: '1', password: 'p' } as Lockfile;
const request = vi.mocked(lcuRequest);

describe('fromRecommended', () => {
  it('핵심 룬 → 주 계열 → 보조 계열 → 파편 순서로 아홉 개를 만든다', () => {
    expect(SELECTION).toEqual({
      primaryStyleId: 8200,
      subStyleId: 8300,
      selectedPerkIds: [8229, 8226, 8210, 8237, 8304, 8345, 5008, 5008, 5011],
    });
  });
});

describe('fromPro', () => {
  it('트리나 핵심 룬이 비어 있으면 넣을 수 없다', () => {
    const pick = {
      runes: {
        primaryTree: null,
        keystone: null,
        primary: [],
        secondaryTree: null,
        secondary: [],
        shards: [],
      },
    } as unknown as TodayPick;

    expect(fromPro(pick)).toBeNull();
  });
});

describe('applyPerkPage', () => {
  it('DFGG 페이지가 있으면 그것만 지우고 새로 만든다', async () => {
    request.mockReset();
    request
      .mockResolvedValueOnce([
        { id: 1, name: '내 페이지', isDeletable: true },
        { id: 2, name: DFGG_PAGE_NAME, isDeletable: true },
      ])
      .mockResolvedValue(null);

    expect(await applyPerkPage(LOCKFILE, SELECTION)).toBeNull();
    expect(request).toHaveBeenCalledWith(LOCKFILE, '/lol-perks/v1/pages/2', { method: 'DELETE' });
    expect(request).toHaveBeenLastCalledWith(LOCKFILE, '/lol-perks/v1/pages', {
      method: 'POST',
      body: { name: DFGG_PAGE_NAME, ...SELECTION, current: true },
    });
  });

  it('DFGG 페이지가 없고 칸도 꽉 찼으면 사용자 페이지를 지우지 않고 no-slot', async () => {
    request.mockReset();
    request
      .mockResolvedValueOnce([{ id: 1, name: '내 페이지', isDeletable: true }])
      .mockResolvedValueOnce({ ownedPageCount: 1 });

    expect(await applyPerkPage(LOCKFILE, SELECTION)).toBe('no-slot');
    expect(request).toHaveBeenCalledTimes(2);
  });

  it('룬이 아홉 개가 아니면 요청하지 않는다', async () => {
    request.mockReset();

    expect(await applyPerkPage(LOCKFILE, { ...SELECTION, selectedPerkIds: [8229] })).toBe('failed');
    expect(request).not.toHaveBeenCalled();
  });
});
