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
  it('트리가 비어 오면 핵심 룬과 보조 룬으로 트리를 찾는다', () => {
    const pick = {
      runes: {
        primaryTree: null,
        keystone: rune(8112),
        primary: [rune(8143), rune(8137), rune(8106)],
        secondaryTree: null,
        secondary: [rune(8473), rune(8242)],
        shards: [rune(5005), rune(5010), rune(5011)],
      },
    } as unknown as TodayPick;

    expect(fromPro(pick)).toEqual({
      primaryStyleId: 8100,
      subStyleId: 8400,
      selectedPerkIds: [8112, 8143, 8137, 8106, 8473, 8242, 5005, 5010, 5011],
    });
  });

  it('핵심 룬이 비어 있으면 넣을 수 없다', () => {
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

  it('DFGG 페이지가 없고 칸도 꽉 찼으면 지금 고른 페이지를 지우고 그 자리에 만든다', async () => {
    request.mockReset();
    request
      .mockResolvedValueOnce([
        { id: 1, name: '내 페이지', isDeletable: true, current: false },
        { id: 2, name: '쓰던 페이지', isDeletable: true, current: true },
      ])
      .mockResolvedValueOnce({ ownedPageCount: 2 })
      .mockResolvedValue(null);

    expect(await applyPerkPage(LOCKFILE, SELECTION)).toBeNull();
    expect(request).toHaveBeenCalledWith(LOCKFILE, '/lol-perks/v1/pages/2', { method: 'DELETE' });
    expect(request).toHaveBeenLastCalledWith(LOCKFILE, '/lol-perks/v1/pages', {
      method: 'POST',
      body: { name: DFGG_PAGE_NAME, ...SELECTION, current: true },
    });
  });

  it('칸이 남아 있으면 아무것도 지우지 않고 만든다', async () => {
    request.mockReset();
    request
      .mockResolvedValueOnce([{ id: 1, name: '내 페이지', isDeletable: true, current: true }])
      .mockResolvedValueOnce({ ownedPageCount: 5 })
      .mockResolvedValue(null);

    expect(await applyPerkPage(LOCKFILE, SELECTION)).toBeNull();
    expect(request).not.toHaveBeenCalledWith(LOCKFILE, expect.stringContaining('/pages/'), {
      method: 'DELETE',
    });
  });

  it('지울 수 있는 페이지가 없는데 칸도 없으면 no-slot', async () => {
    request.mockReset();
    request
      .mockResolvedValueOnce([{ id: 9, name: '기본', isDeletable: false, current: true }])
      .mockResolvedValueOnce({ ownedPageCount: 0 });

    expect(await applyPerkPage(LOCKFILE, SELECTION)).toBe('no-slot');
  });

  it('룬이 아홉 개가 아니면 요청하지 않는다', async () => {
    request.mockReset();

    expect(await applyPerkPage(LOCKFILE, { ...SELECTION, selectedPerkIds: [8229] })).toBe('failed');
    expect(request).not.toHaveBeenCalled();
  });
});
