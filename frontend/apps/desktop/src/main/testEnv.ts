import fs from 'node:fs';

// 테스트 때만 진짜 롤 대신 가짜를 가리키게 하는 값.
export type TestEnv = {
  livePort?: number;
  riotCa?: string;
  ddragonUrl?: string;
  fallbackDirs?: string[]; // 롤 설치 예비 경로. 테스트 중에는 빈 목록으로 두어 롤을 찾지 못하게 한다.
  apiUrl?: string;
  userDataDir?: string;
};

// 소켓
let current: TestEnv | null = null;

export const TEST_ENV_VARS = {
  e2e: 'DFGG_E2E',
  riotCaPath: 'DFGG_RIOT_CA_PATH',
  livePort: 'DFGG_LIVE_PORT',
  ddragonUrl: 'DFGG_DDRAGON_URL',
  apiUrl: 'DFGG_API_URL',
  userDataDir: 'DFGG_USER_DATA_DIR',
} as const;

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
    ddragonUrl: process.env.DFGG_DDRAGON_URL,
    fallbackDirs: [],
    apiUrl: process.env.DFGG_API_URL,
    userDataDir: process.env.DFGG_USER_DATA_DIR,
  });
}
