import fs from 'node:fs';
import path from 'node:path';

// 윈도우 시스템 폴더에서 lol yaml 경로
const PRODUCT_SETTINGS = path.join(
  process.env.PROGRAMDATA ?? 'C:\\ProgramData',
  'Riot Games',
  'Metadata',
  'league_of_legends.live',
  'league_of_legends.live.product_settings.yaml',
);

// ProgramData 경로에 없을 경우를 대비한 경로
export const FALLBACK_DIRS = [
  'C:\\Riot Games\\League of Legends',
  'D:\\Riot Games\\League of Legends',
];

// ProgramData에서 롤 설치 경로 찾는 함수
export function readInstallDirFromMetadata() {
  try {
    const yaml = fs.readFileSync(PRODUCT_SETTINGS, 'utf-8');
    const match = yaml.match(/^product_install_full_path:\s*"(.+)"/m);
    return match ? match[1] : null;
  } catch {
    return null;
  }
}
