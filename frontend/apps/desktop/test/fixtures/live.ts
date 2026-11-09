import type { ActivePlayer, LiveItem, LivePlayer } from '../../src/main/live/types';

const rune = { displayName: '', id: 0, rawDescription: '', rawDisplayName: '' };
const spell = { displayName: '', rawDescription: '', rawDisplayName: '' };

/** Live API 아이템 한 칸. 테스트에 필요한 값만 넘기면 나머지는 기본값으로 채운다. */
export function liveItem(overrides: Partial<LiveItem> = {}): LiveItem {
  return {
    itemID: 1001,
    displayName: '',
    rawDisplayName: '',
    rawDescription: '',
    count: 1,
    slot: 0,
    price: 0,
    canUse: false,
    consumable: false,
    ...overrides,
  };
}

/** Live API 플레이어 한 명. 기본값은 블루팀 미드 가렌. */
export function livePlayer(overrides: Partial<LivePlayer> = {}): LivePlayer {
  return {
    championName: '가렌',
    rawChampionName: 'game_character_displayname_Garen',
    isBot: false,
    isDead: false,
    respawnTimer: 0,
    items: [],
    level: 1,
    position: 'MIDDLE',
    runes: { keystone: rune, primaryRuneTree: rune, secondaryRuneTree: rune },
    scores: { assists: 0, creepScore: 0, deaths: 0, kills: 0, wardScore: 0 },
    skinID: 0,
    summonerName: 'me',
    riotId: 'me#KR1',
    summonerSpells: { summonerSpellOne: spell, summonerSpellTwo: spell },
    team: 'ORDER',
    ...overrides,
  };
}

/** Live API 의 내 정보. DFGG 는 이름과 골드만 읽어서 그 칸만 채운다 */
export function activePlayer(
  overrides: Partial<Pick<ActivePlayer, 'riotId' | 'summonerName' | 'currentGold'>> = {},
) {
  return { riotId: 'me#KR1', summonerName: 'me', currentGold: 500, ...overrides };
}
