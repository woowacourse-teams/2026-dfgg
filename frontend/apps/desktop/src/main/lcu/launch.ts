import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { reportError } from '../sentry';

const RIOT_CLIENT_EXE = 'RiotClientServices.exe';

// 라이엇 클라이언트가 설치되면서 자기 실행 파일 위치를 적어 두는 파일
const INSTALLS_FILE = path.join(
  process.env.PROGRAMDATA ?? 'C:\\ProgramData',
  'Riot Games',
  'RiotClientInstalls.json',
);

// 위 파일이 없을 때를 대비한 기본 설치 경로
const FALLBACK_PATHS = [
  `C:\\Riot Games\\Riot Client\\${RIOT_CLIENT_EXE}`,
  `D:\\Riot Games\\Riot Client\\${RIOT_CLIENT_EXE}`,
];

// 롤을 바로 띄우라고 라이엇 클라이언트에 넘기는 인자
const LAUNCH_ARGS = ['--launch-product=league_of_legends', '--launch-patchline=live'];

function readInstalledPaths(): string[] {
  try {
    const installs: unknown = JSON.parse(fs.readFileSync(INSTALLS_FILE, 'utf-8'));
    if (typeof installs !== 'object' || installs === null) return [];

    const { rc_default: byDefault, rc_live: live } = installs as Record<string, unknown>;
    return [byDefault, live].filter((value): value is string => typeof value === 'string');
  } catch (error) {
    reportError('riot-client-installs', error);
    return [];
  }
}

/** 파일에 적힌 경로를 그대로 실행하지 않는다. 라이엇 클라이언트 실행 파일이 맞고 실제로 있을 때만 쓴다. */
function findRiotClient(): string | null {
  const candidates = [...readInstalledPaths(), ...FALLBACK_PATHS];
  return (
    candidates.find(
      (candidate) => path.basename(candidate) === RIOT_CLIENT_EXE && fs.existsSync(candidate),
    ) ?? null
  );
}

/** 롤 클라이언트를 띄운다. 실행 파일을 못 찾았거나 실행에 실패하면 false. */
export function launchLeagueClient(): boolean {
  const riotClient = findRiotClient();
  if (!riotClient) return false;

  try {
    // 우리 앱이 꺼져도 클라이언트는 남아야 하므로 떼어 놓고 띄운다
    const child = spawn(riotClient, LAUNCH_ARGS, { detached: true, stdio: 'ignore' });
    child.on('error', (error) => reportError('riot-client-launch', error));
    child.unref();
    return true;
  } catch (error) {
    reportError('riot-client-launch', error);
    return false;
  }
}
