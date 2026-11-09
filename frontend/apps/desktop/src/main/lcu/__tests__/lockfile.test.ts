import fs from 'node:fs';
import path from 'node:path';
import { describe, it, vi, beforeEach, afterEach, expect } from 'vitest';
import { setTestEnv } from '../../testEnv';
import { type FakeInstall, writeFakeInstall } from '../../../../test/fake-league/lockfile';
import { getLockfileContent } from '../lockfile';

vi.mock('../../sentry', () => ({ reportError: vi.fn() }));

const LOCKFILE = {
  name: 'LeagueClient',
  pid: '1234',
  port: '54321',
  password: 'pw',
  protocol: 'https',
};

describe('lockfile 찾기', () => {
  let install: FakeInstall;

  beforeEach(() => {
    install = writeFakeInstall(LOCKFILE);
    vi.stubEnv('PROGRAMDATA', install.programDataDir);
    setTestEnv({ fallbackDirs: [] });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    setTestEnv(null);
    install.remove();
  });

  it('ProgramData의 설치 경로를 따라가 lockfile을 읽는다', () => {
    const lockfileContent = getLockfileContent();
    expect(lockfileContent).toMatchObject(LOCKFILE);
  });
  it('lockfile이 없으면 null을 돌려준다', () => {
    fs.rmSync(path.join(install.installDir, 'lockfile'));
    expect(getLockfileContent()).toBeNull();
  });
});
