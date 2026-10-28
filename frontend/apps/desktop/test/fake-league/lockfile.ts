import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import type { Lockfile } from '../../src/shared/types';

// 하네스가 만든 가짜 롤 설치 폴더
export type FakeInstall = {
  programDataDir: string;
  installDir: string;
  remove: () => void;
};

// 롤이 실행될 때 남기는 파일 두 개를 임시 폴더에 흉내 낸다.
export function writeFakeInstall(lockfile?: Lockfile): FakeInstall {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'dfgg-fake-league-'));
  const programDataDir = path.join(root, 'ProgramData');
  const installDir = path.join(root, 'League of Legends');

  // 롤 런처가 남기는 설치 경로 파일
  const metadataDir = path.join(programDataDir, 'Riot Games', 'Metadata', 'league_of_legends.live');
  fs.mkdirSync(metadataDir, { recursive: true });
  fs.writeFileSync(
    path.join(metadataDir, 'league_of_legends.live.product_settings.yaml'),
    `product_install_full_path: "${installDir}"\n`,
  );

  // 롤 클라이언트가 실행 중일 때 남기는 lockfile
  fs.mkdirSync(installDir, { recursive: true });
  if (lockfile) {
    const { name, pid, port, password, protocol } = lockfile;
    fs.writeFileSync(
      path.join(installDir, 'lockfile'),
      `${name}:${pid}:${port}:${password}:${protocol}`,
    );
  }

  return {
    programDataDir,
    installDir,
    remove: () => fs.rmSync(root, { recursive: true, force: true }),
  };
}
