import { describe, it, expect } from 'vitest';
import {
  FALLBACK_SEC,
  POST_SEC,
  PRE_SEC,
  findFightStart,
  planClips,
  type LiveEvent,
  type Stat,
} from './clipPlanner';

const ME = '탑블레이드 가렌';
const stat = (gameTime: number, hp: number, res = 200): Stat => ({
  receivedAt: 0,
  gameTime,
  hp,
  maxHp: 600,
  res,
  resMax: 300,
  resType: 'MANA',
});
// 0~40초, 1초 간격 가짜 데이터
const timeline = (hpAt: (t: number) => number, resAt: (t: number) => number = () => 200) =>
  Array.from({ length: 41 }, (_, t) => stat(t, hpAt(t), resAt(t)));
const kill = (id: number, t: number, killer = ME): LiveEvent => ({
  EventID: id,
  EventName: 'ChampionKill',
  EventTime: t,
  KillerName: killer,
  VictimName: '요릭 봇',
  Assisters: [],
});

describe('findFightStart', () => {
  it('킬에서 거슬러 올라가 첫 교전 신호 직전 시점을 찾는다', () => {
    // 6초에 스킬 사용, 8초부터 맞기 시작, 12초에 킬
    const stats = timeline(
      (t) => (t < 8 ? 600 : 500 - t),
      (t) => (t < 6 ? 200 : 170),
    );
    expect(findFightStart(stats, 12)).toBe(5);
  });

  it('신호 사이 공백이 10초를 넘으면 그 앞은 다른 싸움으로 본다', () => {
    // 3초, 15초에 스킬 사용 → 12초 공백
    const stats = timeline(
      () => 600,
      (t) => (t < 3 ? 200 : t < 15 ? 170 : 140),
    );
    expect(findFightStart(stats, 16)).toBe(14);
  });

  it('작은 체력 감소(미니언)는 교전으로 보지 않는다', () => {
    const stats = timeline((t) => 600 - t * 5); // 1초에 5씩 = 최대 체력의 0.8%
    expect(findFightStart(stats, 20)).toBe(20 - FALLBACK_SEC);
  });

  it('맞기 5초 이상 전에 쓴 스킬(파밍)은 교전 시작으로 보지 않는다', () => {
    // 2초, 5초에 파밍 Q → 12초부터 맞기 시작 → 15초 킬
    const stats = timeline(
      (t) => (t < 12 ? 600 : 600 - (t - 11) * 50),
      (t) => (t < 2 ? 200 : t < 5 ? 170 : 140),
    );
    expect(findFightStart(stats, 15)).toBe(11); // 파밍 Q(1초, 4초)는 무시
  });

  it('신호가 없으면 기본값(FALLBACK_SEC)만큼 앞을 쓴다', () => {
    expect(
      findFightStart(
        timeline(() => 600),
        20,
      ),
    ).toBe(20 - FALLBACK_SEC);
  });
});

describe('planClips', () => {
  const stats = timeline(() => 600);
  // 신호가 없는 데이터라 시작 = 킬 − FALLBACK_SEC − PRE_SEC, 끝 = 킬 + POST_SEC
  const startOf = (t: number) => t - FALLBACK_SEC - PRE_SEC;

  it('내 킬만 클립으로 만든다', () => {
    const clips = planClips([kill(1, 20), kill(2, 30, '요릭 봇')], stats, ME, 0);
    expect(clips).toEqual([{ start: startOf(20), end: 20 + POST_SEC, events: [1] }]);
  });

  it('겹치는 클립은 하나로 합친다', () => {
    const clips = planClips([kill(1, 20), kill(2, 25)], stats, ME, 0);
    expect(clips).toEqual([{ start: startOf(20), end: 25 + POST_SEC, events: [1, 2] }]);
  });

  it('영상 시간 = 게임 시간 + offset', () => {
    const [clip] = planClips([kill(1, 20)], stats, ME, 3);
    expect(clip).toMatchObject({ start: startOf(20) + 3, end: 20 + POST_SEC + 3 });
  });
});
