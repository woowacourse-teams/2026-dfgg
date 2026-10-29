import type { Summoner } from '../../src/shared/types';

export const currentSummoner = (overrides: Partial<Summoner> = {}): Summoner => ({
  puuid: 'puuid-1',
  summonerId: 1,
  displayName: 'me',
  gameName: 'me',
  tagLine: 'KR1',
  summonerLevel: 100,
  profileIconId: 1,
  xpSinceLastLevel: 0,
  xpUntilNextLevel: 1,
  percentCompleteForNextLevel: 0,
  ...overrides,
});
