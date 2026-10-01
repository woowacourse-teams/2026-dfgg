import { fetchCurrentSummoner } from '../lcu/service';
import { onStatusChange } from '../lcu/state';
import { trackEvent } from './umami';

const RETRY_MS = 3000;
const MAX_ATTEMPTS = 10;

export type Identity = { puuid: string; riotId: string };

let identity: Identity | null = null;
let lastConnectedPuuid: string | null = null;
// 재연결되면 이전 조회를 버리기 위한 번호
let generation = 0;

export const getIdentity = () => identity;

// 접속 직후엔 로그인이 덜 끝나 null이 올 수 있어서 재시도한다.
async function resolveIdentity(gen: number, attempt = 1) {
  try {
    const summoner = await fetchCurrentSummoner();
    if (gen !== generation) return;

    if (summoner?.puuid) {
      identity = { puuid: summoner.puuid, riotId: `${summoner.gameName}#${summoner.tagLine}` };

      // 재연결마다 세지 않도록 소환사가 바뀔 때만 보낸다.
      if (lastConnectedPuuid !== identity.puuid) {
        lastConnectedPuuid = identity.puuid;
        trackEvent('desktop-connect', { riotId: identity.riotId, level: summoner.summonerLevel });
      }
      return;
    }
  } catch {
    // 아래에서 재시도
  }

  if (gen !== generation) return;
  if (attempt >= MAX_ATTEMPTS) {
    trackEvent('desktop-error', { type: 'summoner-fetch' });
    return;
  }
  setTimeout(() => resolveIdentity(gen, attempt + 1), RETRY_MS);
}

export function initIdentity() {
  onStatusChange((status) => {
    if (status === 'connected') {
      resolveIdentity(++generation);
    } else if (status === 'disconnected') {
      generation++;
      identity = null;
    }
  });
}
