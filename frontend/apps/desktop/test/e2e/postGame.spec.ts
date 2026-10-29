import fs from 'node:fs';

import { expect, test, type ElectronApplication } from '@playwright/test';

import { TEST_ENV_VARS } from '../../src/main/testEnv';
import { startFakeBackend } from '../fake-backend/server';
import { startFakeLeague } from '../fake-league';
import { lcuGame, lcuMatchHistory } from '../fixtures/matchHistory';
import { currentSummoner } from '../fixtures/summoner';
import { findWindow, launchApp } from './helpers';

const PHASE = '/lol-gameflow/v1/gameflow-phase';
const HISTORY = '/lol-match-history/v1/products/lol/current-summoner/matches';
const OLD_GAME = 100;
const NEW_GAME = 200;

let league: Awaited<ReturnType<typeof startFakeLeague>>;
let backend: Awaited<ReturnType<typeof startFakeBackend>>;
let app: ElectronApplication;
let userDataDir: string;

test.beforeEach(async () => {
  league = await startFakeLeague();
  backend = await startFakeBackend();

  // 홈 화면이 처음 켜질 때 받는 것들. 하나라도 404면 전적 요청 전체가 재시도에 빠진다
  league.lcu.setResponse('GET', PHASE, 'None');
  league.lcu.setResponse('GET', '/lol-summoner/v1/current-summoner', currentSummoner());
  league.lcu.setResponse('GET', '/lol-ranked/v1/current-ranked-stats', { queueMap: {} });
  league.lcu.setResponse('GET', '/lol-game-queues/v1/queues', []);
  league.lcu.setResponse('GET', HISTORY, lcuMatchHistory([lcuGame({ gameId: OLD_GAME })]));

  ({ app, userDataDir } = await launchApp({
    ...league.env,
    [TEST_ENV_VARS.apiUrl]: backend.url,
  }));
});

test.afterEach(async () => {
  await app?.close();
  await league?.close();
  await backend?.close();
  fs.rmSync(userDataDir, { recursive: true, force: true });
});

test('게임이 끝나면 방금 판이 전적에 추가된다', async () => {
  const home = await findWindow(app, 'home');
  const cards = home.locator('li.match');
  await expect(cards).toHaveCount(1, { timeout: 15_000 });

  // 게임이 끝나 클라이언트 전적에 새 판이 들어온 상황
  league.lcu.setResponse(
    'GET',
    HISTORY,
    lcuMatchHistory([lcuGame({ gameId: NEW_GAME }), lcuGame({ gameId: OLD_GAME })]),
  );
  await league.lcu.waitForSubscription('OnJsonApiEvent_lol-gameflow_v1_gameflow-phase');
  league.lcu.emit(PHASE, 'EndOfGame');

  await expect(cards).toHaveCount(2);
});

test('전적을 펼치면 DF 스코어를 받아 보여 준다', async () => {
  league.lcu.setResponse(
    'GET',
    `/lol-match-history/v1/games/${OLD_GAME}`,
    lcuGame({ gameId: OLD_GAME }),
  );
  backend.setResponse('/api/df-score', {
    participants: [{ puuid: 'puuid-1', score: 87.3, badge: 'MVP' }],
  });

  const home = await findWindow(app, 'home');
  await home.locator('li.match button.match-card').first().click({ timeout: 15_000 });

  await expect(home.getByText('DF 87.3')).toBeVisible();
  expect(backend.requests()).toContainEqual(expect.objectContaining({ path: '/api/df-score' }));
});
