import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FakeBackend, startFakeBackend } from '../fake-backend/server.js';
import { setTestEnv } from '../../src/main/testEnv.js';
import { fetchCounters } from '../../src/main/api/champSelect.js';
import { fetchItemRecommendations } from '../../src/main/api/recommendations';

vi.mock('electron', async () => (await import('./electronMock.js')).electronMock);
vi.mock('../../src/main/sentry', () => ({ reportError: vi.fn() }));

describe('가짜 백엔드 연결', () => {
  let backend: FakeBackend;

  beforeEach(async () => {
    backend = await startFakeBackend();
    setTestEnv({ apiUrl: backend.url });
  });

  afterEach(async () => {
    setTestEnv(null);
    await backend.close();
  });

  it('주입한 주소의 가짜 백엔드에 요청하고 응답을 받는다', async () => {
    backend.setResponse('/api/counters', { counters: [] });
    const counters = await fetchCounters('MID', [103], [64], []);

    expect(counters).toMatchObject({ counters: [] });
    expect(backend.requests()[0]).toMatchObject({
      path: '/api/counters',
      body: { myPosition: 'MID', enemies: [103], allies: [64], bans: [] },
    });
  });
  it('추천 요청은 gameTracker 를 거쳐도 동작한다', async () => {
    backend.setResponse('/api/recommendations/v4', { recommendations: [] });
    await expect(fetchItemRecommendations({} as never)).resolves.toEqual({ recommendations: [] });
  });
});
