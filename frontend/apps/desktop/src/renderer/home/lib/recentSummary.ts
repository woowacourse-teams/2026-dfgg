import type { MatchSummary } from '../../../shared/types';
import { kdaValue } from './kda';

const TOP_CHAMPION_COUNT = 3;

export type ChampionRecord = {
  championId: number;
  games: number;
  wins: number;
  /** 0~100 정수 */
  winRate: number;
};

type Tally = { games: number; wins: number; kills: number; deaths: number; assists: number };

const PERCENT = 100;
const EMPTY_TALLY: Tally = { games: 0, wins: 0, kills: 0, deaths: 0, assists: 0 };

const addMatch = (tally: Tally, { win, stats }: MatchSummary): Tally => ({
  games: tally.games + 1,
  wins: tally.wins + Number(win),
  kills: tally.kills + stats.kills,
  deaths: tally.deaths + stats.deaths,
  assists: tally.assists + stats.assists,
});

export type RecentSummary = {
  games: number;
  wins: number;
  losses: number;
  /** 0~100 정수 */
  winRate: number;
  /** 판당 평균 */
  kills: number;
  deaths: number;
  assists: number;
  /** 전체 합으로 낸 평점. 판별 평점의 평균이 아니다 (0데스 판이 평균을 무한대로 만든다). */
  kda: number;
  /** 많이 한 순. 판수가 같으면 더 최근에 한 챔피언이 앞. */
  champions: ChampionRecord[];
  /** 최근 판이 앞. true 가 승리. */
  form: boolean[];
  /** 지금 이어지는 연승(양수) 또는 연패(음수) 수 */
  streak: number;
  /** 가장 잘한 판. 이긴 판이 있으면 그 안에서 고른다. */
  best: MatchSummary;
};

/** 가장 최근 판부터 같은 결과가 몇 판 이어졌는지 센다. */
function currentStreak(form: boolean[]): number {
  const latest = form[0];
  const broken = form.findIndex((win) => win !== latest);
  const length = broken === -1 ? form.length : broken;
  return latest ? length : -length;
}

/** 평점이 높은 판, 같으면 킬 관여(킬+어시스트)가 많은 판이 더 잘한 판이다. */
function isBetter(a: MatchSummary, b: MatchSummary): boolean {
  const [ratioA, ratioB] = [kdaValue(a.stats), kdaValue(b.stats)];
  if (ratioA !== ratioB) return ratioA > ratioB;
  return a.stats.kills + a.stats.assists > b.stats.kills + b.stats.assists;
}

function bestMatch(matches: MatchSummary[]): MatchSummary {
  const wins = matches.filter((match) => match.win);
  const candidates = wins.length > 0 ? wins : matches;
  return candidates.reduce((best, match) => (isBetter(match, best) ? match : best));
}

/** 전적 목록만으로 '최근 N판' 요약을 만든다. 따로 받아 오는 값은 없다. */
export function summarizeMatches(matches: MatchSummary[]): RecentSummary | null {
  if (matches.length === 0) return null;

  const total = matches.reduce(addMatch, EMPTY_TALLY);
  const { games, wins } = total;

  // Map 은 넣은 순서를 지키고 목록은 최근 판이 앞이라, 안정 정렬만으로 동률이 최근 순이 된다.
  const byChampion = new Map<number, Tally>();
  for (const match of matches) {
    byChampion.set(
      match.championId,
      addMatch(byChampion.get(match.championId) ?? EMPTY_TALLY, match),
    );
  }

  return {
    games,
    wins,
    losses: games - wins,
    winRate: Math.round((wins / games) * PERCENT),
    kills: total.kills / games,
    deaths: total.deaths / games,
    assists: total.assists / games,
    kda: kdaValue(total),
    champions: [...byChampion.entries()]
      .sort(([, a], [, b]) => b.games - a.games)
      .slice(0, TOP_CHAMPION_COUNT)
      .map(([championId, tally]) => ({
        championId,
        games: tally.games,
        wins: tally.wins,
        winRate: Math.round((tally.wins / tally.games) * PERCENT),
      })),
    form: matches.map((match) => match.win),
    streak: currentStreak(matches.map((match) => match.win)),
    best: bestMatch(matches),
  };
}
