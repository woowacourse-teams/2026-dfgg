import fs from 'node:fs';
import path from 'node:path';
import { reportError } from '../sentry';
import { getTestEnv } from '../testEnv';

// 윈도우 시스템 폴더에서 lol yaml 경로. 테스트가 PROGRAMDATA를 바꿀 수 있게 읽을 때마다 계산한다.
const productSettingsPath = () =>
  path.join(
    process.env.PROGRAMDATA ?? 'C:\\ProgramData',
    'Riot Games',
    'Metadata',
    'league_of_legends.live',
    'league_of_legends.live.product_settings.yaml',
  );

// ProgramData 경로에 없을 경우를 대비한 경로
const FALLBACK_DIRS = ['C:\\Riot Games\\League of Legends', 'D:\\Riot Games\\League of Legends'];

export const fallbackDirs = () => getTestEnv()?.fallbackDirs ?? FALLBACK_DIRS;

// ProgramData에서 롤 설치 경로 찾는 함수
export function readInstallDirFromMetadata() {
  try {
    const yaml = fs.readFileSync(productSettingsPath(), 'utf-8');
    const match = yaml.match(/^product_install_full_path:\s*"(.+)"/m);
    return match ? match[1] : null;
  } catch (error) {
    reportError('lcu-install-dir', error);
    return null;
  }
}
