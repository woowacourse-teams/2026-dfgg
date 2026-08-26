import https from 'node:https';

import type { Lineup, LineupSlot, Position } from './types';

/** 인게임 전용 로컬 API. 포트가 2999로 고정이고 인증이 필요 없다. */
const LIVE_CLIENT_PORT = 2999;

/** 인게임 API가 쓰는 포지션 문자열 → 백엔드 포지션 */
const POSITION_BY_LIVE: Record<string, Position> = {
  TOP: 'TOP',
  JUNGLE: 'JUNGLE',
  MIDDLE: 'MID',
  BOTTOM: 'BOTTOM',
  UTILITY: 'SUPPORT',
};

interface LiveItem {
  itemID?: number;
  /** 와드 토큰처럼 소모되거나 쓰는 아이템. 완성 빌드 체크에는 세지 않는다. */
  consumable?: boolean;
}

interface LivePlayer {
  championName?: string;
  team?: string;
  position?: string;
  riotIdGameName?: string;
  summonerName?: string;
  items?: LiveItem[];
}

interface LiveGameData {
  activePlayer?: { riotIdGameName?: string; summonerName?: string };
  allPlayers?: LivePlayer[];
}

function request<T>(path: string): Promise<T> {
  return new Promise((resolve, reject) => {
    // 롤 클라이언트와 마찬가지로 자체 서명 인증서를 쓴다.
    const req = https.request(
      { host: '127.0.0.1', port: LIVE_CLIENT_PORT, path, method: 'GET', rejectUnauthorized: false },
      (res) => {
        let body = '';
        res.on('data', (chunk) => (body += chunk));
        res.on('end', () => {
          if (res.statusCode !== 200) {
            reject(new Error(`live-client ${path} ${res.statusCode}`));
            return;
          }
          try {
            resolve(JSON.parse(body));
          } catch (error) {
            reject(error);
          }
        });
      },
    );
    req.on('error', reject);
    req.end();
  });
}

function toSlot(player: LivePlayer): LineupSlot {
  return {
    cellId: -1,
    championId: 0,
    championName: player.championName ?? null,
    position: POSITION_BY_LIVE[player.position?.toUpperCase() ?? ''] ?? null,
  };
}

/** 이름 뒤에 붙는 #KR1 같은 태그를 떼어 activePlayer와 allPlayers를 맞춘다. */
function bareName(name: string | undefined): string {
  return (name ?? '').split('#')[0].trim();
}

/** 완성 아이템 id만 남긴다. 와드류 소모품은 빌드 체크 대상이 아니다. */
function completedItemIds(items: LiveItem[] | undefined): number[] {
  return (items ?? [])
    .filter((item) => !item.consumable && typeof item.itemID === 'number')
    .map((item) => item.itemID as number);
}

/**
 * 진행 중인 게임의 조합을 읽는다. 게임 중이 아니면 연결이 거부되므로 null.
 *
 * 밴픽 API와 달리 양 팀 10명의 포지션이 모두 채워져 나온다.
 */
export async function fetchLiveGame(): Promise<Lineup | null> {
  let data: LiveGameData;
  try {
    data = await request<LiveGameData>('/liveclientdata/allgamedata');
  } catch {
    return null;
  }

  const players = data.allPlayers ?? [];
  if (players.length === 0) return null;

  const myName = bareName(data.activePlayer?.riotIdGameName ?? data.activePlayer?.summonerName);
  const me = players.find(
    (player) => bareName(player.riotIdGameName ?? player.summonerName) === myName,
  );
  if (!me) return null;

  const myTeam = me.team;
  const allies = players.filter((player) => player.team === myTeam).map(toSlot);
  const enemies = players.filter((player) => player.team !== myTeam).map(toSlot);

  return {
    sessionId: 0, // main.ts 가 실제 값으로 덮어쓴다
    source: 'in-game',
    myCellId: -1,
    myChampionId: 0,
    myChampionName: me.championName ?? null,
    myPosition: POSITION_BY_LIVE[me.position?.toUpperCase() ?? ''] ?? null,
    myItemIds: completedItemIds(me.items),
    allies,
    enemies,
  };
}
