import fs from 'node:fs';

import { expect, test, type ElectronApplication } from '@playwright/test';

import { TEST_ENV_VARS } from '../../src/main/testEnv';
import { startFakeBackend } from '../fake-backend/server';
import { startFakeLeague } from '../fake-league';
import { action, champSelectSession, ME, player } from '../fixtures/champSelect';
import { findWindow, launchApp } from './helpers';

const SESSION_PATH = '/lol-champ-select/v1/session';
const MY_PICK_ID = 7;

let league: Awaited<ReturnType<typeof startFakeLeague>>;
let backend: Awaited<ReturnType<typeof startFakeBackend>>;
let app: ElectronApplication;
let userDataDir: string;

test.beforeEach(async () => {
  league = await startFakeLeague();
  backend = await startFakeBackend();

  league.lcu.setResponse('GET', '/lol-gameflow/v1/gameflow-phase', 'ChampSelect');
  league.lcu.setResponse('GET', '/lol-summoner/v1/current-summoner', {
    puuid: 'puuid-me',
    summonerId: 1,
    displayName: 'me',
    gameName: 'me',
    tagLine: 'KR1',
    summonerLevel: 100,
    profileIconId: 1,
    xpSinceLastLevel: 0,
    xpUntilNextLevel: 1,
    percentCompleteForNextLevel: 0,
  });
  league.lcu.setResponse(
    'GET',
    SESSION_PATH,
    champSelectSession({
      myTeam: [player({ cellId: ME, championId: 0, assignedPosition: 'middle' })],
      theirTeam: [player({ cellId: 5, championId: 103, assignedPosition: '' })],
      actions: [[action({ id: MY_PICK_ID, actorCellId: ME, type: 'pick', isInProgress: true })]],
    }),
  );
  league.lcu.setResponse('PATCH', `${SESSION_PATH}/actions/${MY_PICK_ID}`, {});
  backend.setResponse('/api/counters', {
    patch: '15.20',
    myPosition: 'MID',
    enemies: [
      {
        championId: 103,
        name: '아리',
        positions: [{ position: 'MID', rate: 90 }],
        counters: [{ championId: 90, name: '말자하' }],
      },
    ],
  });

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

test('밴픽에 들어가면 홈 창에 상대와 카운터 챔피언이 보인다', async () => {
  const home = await findWindow(app, 'home');

  await expect(home.getByText('카운터 추천')).toBeVisible({ timeout: 15_000 });
  await expect(home.getByRole('button', { name: '말자하' })).toBeVisible();
});

test('카운터 챔피언을 누르면 클라이언트에 그 챔피언을 고르라고 보낸다', async () => {
  const home = await findWindow(app, 'home');

  await home.getByRole('button', { name: '말자하' }).click({ timeout: 15_000 });

  await expect
    .poll(() => league.lcu.requests())
    .toContainEqual(
      expect.objectContaining({
        method: 'PATCH',
        path: `${SESSION_PATH}/actions/${MY_PICK_ID}`,
      }),
    );
});
