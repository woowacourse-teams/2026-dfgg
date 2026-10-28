import type { TestEnv } from '../../src/main/testEnv';
import { CHAMPIONS, ITEMS } from '../fixtures/ddragon';
import { FAKE_CERT, FAKE_CERT_PATH } from './certs';
import { type FakeDDragon, type FakeDDragonData, startFakeDDragon } from './ddragonServer';
import { type FakeLcu, startFakeLcu } from './lcuServer';
import { type FakeLive, startFakeLive } from './liveServer';
import { type FakeInstall, writeFakeInstall } from './lockfile';

// 가짜 롤 전체
export type FakeLeague = {
  lcu: FakeLcu;
  live: FakeLive;
  ddragon: FakeDDragon;
  install: FakeInstall;
  testEnv: TestEnv;
  env: Record<string, string>;
  close: () => Promise<void>;
};

export async function startFakeLeague(
  ddragonData: FakeDDragonData = { version: '15.20.1', champions: CHAMPIONS, items: ITEMS },
): Promise<FakeLeague> {
  const [lcu, live, ddragon] = await Promise.all([
    startFakeLcu(),
    startFakeLive(),
    startFakeDDragon(ddragonData),
  ]);

  const install = writeFakeInstall(lcu.lockfile);

  return {
    lcu,
    live,
    ddragon,
    install,
    testEnv: {
      riotCa: FAKE_CERT,
      livePort: live.port,
      ddragonUrl: ddragon.url,
      fallbackDirs: [],
    },
    env: {
      DFGG_E2E: '1',
      DFGG_RIOT_CA_PATH: FAKE_CERT_PATH,
      DFGG_LIVE_PORT: String(live.port),
      DFGG_DDRAGON_URL: ddragon.url,
      PROGRAMDATA: install.programDataDir,
    },
    close: async () => {
      await Promise.all([lcu.close(), live.close(), ddragon.close()]);
      install.remove();
    },
  };
}
