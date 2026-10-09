import fs from 'node:fs';
import path from 'node:path';
import { liveRequest } from '../live/client';

const POLL_MS = 1_000; // 1초마다 확인

// Live API 이벤트 하나. 이벤트 종류마다 필드가 달라서 나머지는 unkown으로 둠
interface LiveEvent {
  EventID: number;
  EventName: string;
  EventTime: number;
  [key: string]: unknown; // 다양한 필드가 올 수 있음
}

interface ActivePlayerStats {
  riotId: string;
  riotIdGameName: string;
  championStats: {
    currentHealth: number;
    maxHealth: number;
    resourceValue: number;
    resourceMax: number;
    resourceType: string;
  };
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

// 지금 돌고 있는 수집기를 멈추는 함수
let stopCurrent: (() => void) | null = null;

// 녹화 시작 시 호출: 세션 폴더에 events.jsonl, me.json 기록 시작
export function startEventCollector(dir: string) {
  stopEventCollector(); // 이전 수집기가 남아 있으면 정리

  // 수집기마다 자기 상태를 따로 가짐 -> 이전 수집기와 섞이지 않음
  let active = true; // 수집기를 활성 상태로 둔다.
  let lastId = -1; // 마지막으로 저장한 EventID. 새로운 이벤트만 이어 쓴다.
  let savedMe = false; // 이벤트마다 내가 새로 써지니깐 그것을 막는다.
  // 킬, 데스 관련 데이터를 모은다.
  const out = fs.createWriteStream(path.join(dir, 'events.jsonl'), { flags: 'a' });
  // 챔피언 체력 감소, 마나 사용 데이터를 모은다.
  const statsOut = fs.createWriteStream(path.join(dir, 'stats.jsonl'), { flags: 'a' });

  void (async () => {
    while (active) {
      try {
        const receivedAt = Date.now();

        // 3개를 동시에 요청 (따로 하면 시각 차이 발생)
        const [game, me, data] = await Promise.all([
          liveRequest<{ gameTime: number }>('/liveclientdata/gamestats'),
          liveRequest<ActivePlayerStats>('/liveclientdata/activeplayer'),
          liveRequest<{ Events: LiveEvent[] }>('/liveclientdata/eventdata'),
        ]);

        // 1. 내 정보 한 번만 저장 (나중에 내 이벤트를 거를 때 사용)
        if (!savedMe && me) {
          const { riotId, riotIdGameName } = me;
          // 내 정보를 me.json에 한 번만 저장한다. 저장할 때는 가공 없이(null), 들여쓰기(2)로 보기 좋게 저장한다.
          fs.writeFileSync(
            path.join(dir, 'me.json'),
            JSON.stringify({ riotId, riotIdGameName }, null, 2),
          );
          savedMe = true;
        }

        // 상태 기록: 교전 시작 찾기 + 시간 맞추기에 사용
        if (game && me) {
          const s = me.championStats;
          statsOut.write(
            JSON.stringify({
              receivedAt, // 우리 시각(ms)
              gameTime: game.gameTime, // 게임 시간(초)
              hp: s.currentHealth,
              maxHp: s.maxHealth,
              res: s.resourceValue, // 마나/기력
              resMax: s.resourceMax,
              resType: s.resourceType, // MANA, ENERGY, NONE 등
            }) + '\n',
          );
        }

        // 새 이벤트 저장
        for (const e of data?.Events ?? []) {
          if (e.EventID <= lastId) continue; // 이미 저장된 이벤트면 패스
          lastId = e.EventID; // 마지막 기록 id 업데이트
          // receivedAt: 우리가 받은 시각(ms). 다음 단계에서 영상 시간과 맞출 때 사용
          out.write(JSON.stringify({ ...e, receivedAt: Date.now() }) + '\n');
          console.log('[event]', e.EventID, e.EventName, e.EventTime.toFixed(1));
        }
      } catch (e) {
        console.log('[event] 대기 중', (e as Error).message);
      }
      // 1초마다 while문을 반복한다.
      await sleep(POLL_MS);
    }
    // 반복이 끝난 뒤에 파일 닫기 -> 닫힌 파일에 쓰는 일이 없음
    out.end();
    statsOut.end();
  })();

  // 종료되면 수집기 끄기
  stopCurrent = () => {
    active = false;
  };
}

// 녹화 종료 시 호출
export function stopEventCollector() {
  stopCurrent?.();
  stopCurrent = null;
}
