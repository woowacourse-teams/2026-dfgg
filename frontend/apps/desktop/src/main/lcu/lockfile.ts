import fs from 'node:fs';
import path from 'node:path';
import { readInstallDirFromMetadata, FALLBACK_DIRS } from './credentials';

// 롤 폴더에서 lock file 경로를 찾는 함수
function findLockfilePath() {
  const candidates = [readInstallDirFromMetadata(), ...FALLBACK_DIRS]
    .filter((dir): dir is string => dir !== null)
    .map((dir) => path.join(dir, 'lockfile'));

  return candidates.find((p) => fs.existsSync(p)) ?? null;
}

// lock file의 정보를 가져오는 함수
export function getLockfileContent() {
  const lockfile = { name: '', pid: '', port: '', password: '', protocol: '' };
  const lockFilePath = findLockfilePath();

  if (!lockFilePath) return null;

  try {
    if (fs.existsSync(lockFilePath)) {
      const lockfileContent = fs.readFileSync(lockFilePath, 'utf-8');

      const [name, pid, port, password, protocol] = lockfileContent.split(':');
      lockfile.name = name ?? null;
      lockfile.pid = pid ?? null;
      lockfile.port = port ?? null;
      lockfile.password = password ?? null;
      lockfile.protocol = protocol ?? null;

      console.log(`port: ${port}`);
      console.log(`password: ${password}`);
    }
  } catch {
    return null;
  }

  return lockfile;
}
