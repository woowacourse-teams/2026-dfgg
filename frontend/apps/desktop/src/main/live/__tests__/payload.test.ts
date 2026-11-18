import { describe, expect, it, vi } from 'vitest';

import { buildRecommendationBody } from '../payload';
import type { LivePlayer } from '../types';

// payload 는 umami 를 불러와서 테스트에서는 비워 둔다.
vi.mock('../../analytics/umami', () => ({ trackEvent: vi.fn() }));

const player = (riotId: string, team: string, position: LivePlayer['position']) =>
  ({
    riotId,
    summonerName: riotId,
    rawChampionName: 'game_character_displayname_Garen',
    team,
    position,
    items: [],
  }) as unknown as LivePlayer;

const build = (players: LivePlayer[], queueId?: number) =>
  buildRecommendationBody({
    players,
    myRiotId: 'me#KR1',
    patch: '16.22.700.1234',
    purchasedItemIds: new Set(),
    championNames: { Garen: 'Garen' },
    availableGold: 500,
    gameTimeMs: 0,
    queueId,
  });

describe('buildRecommendationBody', () => {
  it('지금 판의 큐 id 를 그대로 담는다', () => {
    const body = build([player('me#KR1', 'ORDER', 'TOP')], 420);

    expect(body?.queueId).toBe(420);
  });

  it('훈련모드는 혼자이고 포지션이 비어 있어도 큐 id 와 함께 보낸다', () => {
    const body = build([player('me#KR1', 'ORDER', '')], 3140);

    expect(body).toMatchObject({
      myChampion: { name: 'Garen', position: '' },
      allies: [],
      enemies: [],
      queueId: 3140,
    });
  });

  it('큐를 모르면 큐 id 를 싣지 않는다', () => {
    const body = build([player('me#KR1', 'ORDER', 'TOP')]);

    expect(body).not.toHaveProperty('queueId');
  });
});
