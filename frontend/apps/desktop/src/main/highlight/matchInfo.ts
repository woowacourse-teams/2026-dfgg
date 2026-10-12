import fs from 'node:fs';
import path from 'node:path';
import { fetchGameflowSession } from '../lcu/service';

// match.josn: 이 세션이 어느 판인지 확인
export interface MatchInfo {
  gameId: number; // 어느 판인지 확인
  queueId: number; // 큐 종류 확인
}

const RETRY = 3; // 최대 시도 횟수
const RETRY_MS = 2_000; // 실패 시 기다리는 시간 2초

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// 녹화 시작 직후 호출: 지금 게임의 gameId를 세션 폴더에 match.json으로 저장
export async function saveMatchInfo(dir: string): Promise<void> {
  for (let i = 0; i < RETRY; i++) {
    try {
      const session = await fetchGameflowSession();
      const gameId = session?.gameData.gameId;
      if (session && gameId) {
        const info = { gameId, queueId: session.gameData.queue.id };
        fs.writeFileSync(path.join(dir, 'match.json'), JSON.stringify(info));
        return;
      }
    } catch {
      console.warn('게임 정보 match.json에 저장 실패');
    }
    await sleep(RETRY_MS);
  }
  // 끝까지 실패하면 match.json 없이 진행 (시간 매칭으로 나중에 채움)
  console.warn('[highlight] gameId를 가져오지 못함', dir);
}
