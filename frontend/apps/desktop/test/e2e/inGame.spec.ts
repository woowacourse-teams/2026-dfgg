import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { _electron as electron, type ElectronApplication, expect, test } from '@playwright/test';
import { findWindow } from './helpers';

import { type FakeBackend, startFakeBackend } from '../fake-backend/server';
import { type FakeLeague, startFakeLeague } from '../fake-league';
import { activePlayer, liveItem, livePlayer } from '../fixtures/live';
import { recommendedItem } from '../fixtures/recommendation';

const APP_DIR = path.resolve(__dirname, '../..');

let league: FakeLeague;
let backend: FakeBackend;
let userDataDir: string;
let app: ElectronApplication;

test.beforeEach(async () => {
  [league, backend] = await Promise.all([startFakeLeague(), startFakeBackend()]);
  userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dfgg-e2e-userdata-'));

  // 롤 클라이언트: 게임 중, 패치 15.20
  league.lcu.setResponse('GET', '/lol-gameflow/v1/gameflow-phase', 'InProgress');
  league.lcu.setResponse('GET', '/lol-patch/v1/game-version', '15.20.700.1234');

  // 게임: 나(가렌, 미드)와 상대(오공)
  league.live.setResponse('/liveclientdata/activeplayer', activePlayer({ currentGold: 1300 }));
  league.live.setResponse('/liveclientdata/playerlist', [
    livePlayer({ items: [liveItem({ itemID: 1036 })] }),
    livePlayer({
      riotId: 'enemy#KR1',
      summonerName: 'enemy',
      team: 'CHAOS',
      rawChampionName: 'game_character_displayname_MonkeyKing',
    }),
  ]);
  league.live.setResponse('/liveclientdata/gamestats', { gameTime: 300 });

  backend.setResponse('/api/recommendations/v4', {
    recommendedItems: [recommendedItem()],
    situationalItems: [],
  });

  app = await electron.launch({
    args: ['.'],
    cwd: APP_DIR,
    env: {
      ...process.env,
      ...league.env,
      DFGG_API_URL: backend.url,
      DFGG_USER_DATA_DIR: userDataDir,
    },
  });
});

test.afterEach(async () => {
  await app.close();
  await Promise.all([league.close(), backend.close()]);
  fs.rmSync(userDataDir, { recursive: true, force: true });
});

test('게임이 시작되면 overlay에 추천 아이템이 보인다', async () => {
  const overlay = await findWindow(app, 'overlay');
  await expect(overlay.getByText('무한의 대검')).toBeVisible({ timeout: 15_000 });
});
