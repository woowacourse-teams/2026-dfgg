import { getLockfileContent } from './lockfile';
import { getCurrentSummoner, getGameflowPhase, getGameVersion } from './endpoints';
import { GameflowPhase, Lockfile, Summoner } from '../../types';

async function withLockfile<T>(
  errorLabel: string,
  callback: (lockfileContent: Lockfile) => Promise<T>,
) {
  const lockfileContent = getLockfileContent();

  if (!lockfileContent) {
    console.error('클라이언트 연결 실패');
    return null;
  }

  try {
    return await callback(lockfileContent);
  } catch (error) {
    console.error(errorLabel, error);
    throw error;
  }
}

// lockfile 찾고 현재 소환사 정보 요청하기
export function fetchCurrentSummoner() {
  return withLockfile<Summoner | null>('소환사 정보 요청 실패', (lockfileContent) => {
    return getCurrentSummoner(lockfileContent);
  });
}

// 앱 최초 실행 시 현재 phase 가져오기
export function fetchGameflowPhase() {
  return withLockfile<GameflowPhase | null>('현재 game flow phase 요청 실패', (lockfileContent) => {
    return getGameflowPhase(lockfileContent);
  });
}

export function fetchGameVersion() {
  return withLockfile('패치버전 요청 실패', (lockfile) => getGameVersion(lockfile));
}
