import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { _electron as electron, type ElectronApplication, expect, test } from '@playwright/test';

import { type FakeBackend, startFakeBackend } from '../fake-backend/server';
import { type FakeLeague, startFakeLeague } from '../fake-league';

const APP_DIR = path.resolve(__dirname, '../../');

let league: FakeLeague;
let backend: FakeBackend;
let userDataDir: string;
let app: ElectronApplication;

test.beforeEach(async () => {
  [league, backend] = await Promise.all([startFakeLeague(), startFakeBackend()]);
  userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dfgg-e2e-userdata-'));

  // 롤 클라이언트: 켜져 있고 로비에 있다
  league.lcu.setResponse('GET', '/lol-gameflow/v1/gameflow-phase', 'Lobby');

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

test('테스트 모드로 켜면 home 창과 overlay 창을 빌드된 화면으로 만든다', async () => {
  await expect
    .poll(() => app.windows().map((page) => page.url()))
    .toEqual(
      expect.arrayContaining([
        expect.stringContaining('/out/renderer/home/index.html'),
        expect.stringContaining('/out/renderer/overlay/index.html'),
      ]),
    );
});

test('진짜 사용자 폴더 대신 넘겨준 폴더에 기록한다', async () => {
  await expect.poll(() => fs.existsSync(path.join(userDataDir, 'analytics.json'))).toBe(true);
});
