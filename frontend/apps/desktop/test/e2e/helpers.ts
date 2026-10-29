import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

import {
  _electron as electron,
  expect,
  type ElectronApplication,
  type Page,
} from '@playwright/test';

import { TEST_ENV_VARS } from '../../src/main/testEnv';

const APP_DIR = path.resolve(__dirname, '../..');

export async function launchApp(env: Record<string, string>) {
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'dfgg-e2e-'));
  const app = await electron.launch({
    args: ['.'],
    cwd: APP_DIR,
    env: { ...process.env, ...env, [TEST_ENV_VARS.userDataDir]: userDataDir },
  });
  return { app, userDataDir };
}

export async function findWindow(app: ElectronApplication, name: 'home' | 'overlay') {
  let found: Page | undefined;
  await expect
    .poll(() => {
      found = app.windows().find((page) => page.url().includes(`/renderer/${name}/`));
      return found;
    })
    .toBeDefined();
  return found as Page;
}
