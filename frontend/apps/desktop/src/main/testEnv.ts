import fs from 'node:fs';

// 테스트 때만 진짜 롤 대신 가짜를 가리키게 하는 값.
export type TestEnv = {
  livePort?: number;
  riotCa?: string;
  ddragonUrl?: string;
};

// 소켓
let current: TestEnv | null = null;

export const getTestEnv = () => current;

export function setTestEnv(env: TestEnv | null) {
  current = env;
}

// E2E 때 환경변수에서 읽어 꽂는다. main.ts가 앱 시작 직후 부른다.
// 배포 앱에서는 절대 켜지지 않는다.
export function loadTestEnvFromProcess(isPackaged: boolean) {
  if (isPackaged || process.env.DFGG_E2E !== '1') return;

  const caPath = process.env.DFGG_RIOT_CA_PATH;
  setTestEnv({
    riotCa: caPath ? fs.readFileSync(caPath, 'utf-8') : undefined,
    livePort: Number(process.env.DFGG_LIVE_PORT) || undefined,
    ddragonUrl: process.env.DFGG_DRAGON_URL,
  });
}
