export interface LiveEvent {
  EventID: number;
  EventName: string;
  EventTime: number;
  [key: string]: unknown;
}

export interface Stat {
  receivedAt: number;
  gameTime: number;
  hp: number;
  maxHp: number;
  res: number;
  resMax: number;
  resType: string;
}

export interface Clip {
  start: number;
  end: number;
  events: number[];
} // 영상 기준 초, 포함된 EventID

const LOOKBACK_SEC = 30; // 킬에서 최대 30초 전까지만 거슬러 올라감
const GAP_SEC = 10; // 신호 사이가 10초 이하면 같은 싸움
export const PRE_SEC = 3; // 평타는 마나를 안 써서 감지가 2 ~ 3초 늦음. 3초 앞당김
export const POST_SEC = 4; // 킬 후 4초 더 보여줌
export const FALLBACK_SEC = 15; // 신호가 없으면 킬 15초 전부터
const HP_DROP_RATIO = 0.04; // 최대 체력 4% 미만 감소는 미니언으로 보고 무시
const RES_DROP = 10; // 마나, 기력 10이상 감소 = 스킬 사용
const SPELL_LEAD_SEC = 5; // 마나만 줄어든 신호는 맞기 (또는 킬) 5초 전까지만 인정

// 하이라이트에 넣을 내 이벤트인지 (KillerName은 태그 없은 riotIdGameName과 같음)
export function isMyHighlight(e: LiveEvent, me: string) {
  switch (e.EventName) {
    case 'ChampionKill':
      return e.KillerName === me || (Array.isArray(e.Assisters) && e.Assisters.includes(me));
    case 'DragonKill':
    case 'BaronKill':
    case 'HeraldKill':
      return e.KillerName === me;
    default:
      return false;
  }
}

// 1초 사이에 교전 신호가 있었는지(없으면 null, 있으면 시각과 "체력이 깎였는지")
function tickAt(prev: Stat, cur: Stat) {
  const hpHit = prev.hp - cur.hp >= cur.maxHp * HP_DROP_RATIO; // 의미 있게 맞음
  const spell = cur.resType !== 'NONE' && prev.res - cur.res >= RES_DROP; // 스킬 사용
  if (!hpHit && !spell) return null;
  return { t: prev.gameTime, hpHit };
}

// 이벤트 시점에서 거슬러 올라가며 교전 시작(게임 시간)을 찾음. 매개변수: (게임 통계, 킬 시작 시간)
export function findFightStart(stats: Stat[], eventTime: number) {
  let start: number | null = null;
  let last = eventTime; // 가장 최근에 인정한 신호
  let lastHit = eventTime; // 가장 최근에 맞은 신호 (킬 시점에서 시작)

  for (let i = stats.length - 1; i > 0; i--) {
    const prev = stats[i - 1];
    const cur = stats[i];
    if (cur.gameTime > eventTime + 1) continue; // 킬 이후 시간이면 건너뛰기. 시작점을 찾고 있기 때문
    if (eventTime - prev.gameTime > LOOKBACK_SEC) break; // 30초보다 더 이전 시간이면 벗어나기.

    const tick = tickAt(prev, cur);
    if (!tick) continue;
    if (last - tick.t > GAP_SEC) break; // 10초 넘게 조용하면 다른 싸움으로 넘어감
    if (!tick.hpHit && lastHit - tick.t > SPELL_LEAD_SEC) continue; // 맞기 5초 이상 전 스킬은 파밍으로 보고 무시

    start = tick.t; // 교전 시작을 이 시각으로 당김
    last = tick.t; // 다음 신호와의 공백은 여기부터 잼
    if (tick.hpHit) lastHit = tick.t; // 맞은 신호면 마지막으로 맞은 시각도 갱신
  }
  return start ?? eventTime - FALLBACK_SEC; // 시작 시간이 유추되었으면 그걸로 하고 아니면 15초 전으로 고정
}

// 영상 시간 = 게임 시간 + offset.
export function videoOffset(stats: Stat[], startedAt: number) {
  const diffs = stats
    .map((s) => (s.receivedAt - startedAt) / 1000 - s.gameTime) // 1초마다 게임 시간과 영상 시간 차이 확인
    .sort((a, b) => a - b); // 올림 차순 정렬
  return diffs[Math.floor(diffs.length / 2)] ?? 0; // 가운데 값 사용하기
}

// 촤종: 자를 구간 목록 (영상 기준 초)
export function planClips(events: LiveEvent[], stats: Stat[], me: string, offset: number): Clip[] {
  const raw = events
    .filter((e) => isMyHighlight(e, me))
    .map((e) => ({
      start: Math.max(0, findFightStart(stats, e.EventTime) - PRE_SEC + offset),
      end: e.EventTime + POST_SEC + offset,
      events: [e.EventID],
    }))
    .sort((a, b) => a.start - b.start);

  // 겹치는 구간은 합침 (멀티킬, 연속 교전)
  const merged: Clip[] = [];
  for (const c of raw) {
    const prev = merged.at(-1); // 결과 목록의 마지막 클립
    if (prev && c.start <= prev.end) {
      // 겹치면
      prev.end = Math.max(prev.end, c.end); // 끝을 늘려서 하나로 합침
      prev.events.push(...c.events); // 이벤트 id도 합침
    } else {
      // 안 겹치면
      merged.push({ ...c, events: [...c.events] }); // 새 클립으로 추가
    }
  }
  return merged;
}
