export interface DDragonItemList {
  type: 'item';
  version: string;
  data: Record<string, DDragonItem>; // 키 = '3006' 처럼 id 문자열
}

export interface DDragonItem {
  name: string;
  description: string;
  gold: { base: number; total: number; sell: number; purchasable: boolean };
  tags: string[]; // 'Boots', 'Damage' 등
  from?: string[]; // 재료 id 목록
  into?: string[]; // 이걸로 만들 수 있는 상위 아이템
  depth?: number;
  maps: Record<string, boolean>;
}

export interface DDragonChampionList {
  type: 'champion';
  format: string; // 'standAloneComplex'
  version: string; // '16.18.1'
  data: Record<string, DDragonChampion>; // 키 = champion id ('Garen', 'MonkeyKing' ...)
}

export interface DDragonChampion {
  version: string;
  id: string; // 'Garen'  ← Live API rawChampionName과 매칭
  key: string; // '86'     ← LCU championId와 매칭 (문자열!)
  name: string; // 'Garen'
  title: string; // 'the Might of Demacia'
  blurb: string;
  info: {
    attack: number;
    defense: number;
    magic: number;
    difficulty: number;
  };
  image: {
    full: string; // 'Garen.png'
    sprite: string;
    group: string;
    x: number;
    y: number;
    w: number;
    h: number;
  };
  tags: string[]; // 'Fighter' | 'Tank' | 'Mage' | 'Assassin' | 'Marksman' | 'Support'
  partype: string; // 'Mana', 'Energy', 'None' 등
  stats: {
    hp: number;
    hpperlevel: number;
    mp: number;
    mpperlevel: number;
    movespeed: number;
    armor: number;
    armorperlevel: number;
    spellblock: number;
    spellblockperlevel: number;
    attackrange: number;
    hpregen: number;
    hpregenperlevel: number;
    mpregen: number;
    mpregenperlevel: number;
    crit: number;
    critperlevel: number;
    attackdamage: number;
    attackdamageperlevel: number;
    attackspeedperlevel: number;
    attackspeed: number;
  };
}

export type DDragonVersions = string[];

export type GameVersion = string;

// ---------- 공통 ----------
export interface ChampionStats {
  abilityHaste: number;
  abilityPower: number;
  armor: number;
  armorPenetrationFlat: number;
  armorPenetrationPercent: number;
  attackDamage: number;
  attackRange: number;
  attackSpeed: number;
  bonusArmorPenetrationPercent: number;
  bonusMagicPenetrationPercent: number;
  critChance: number;
  critDamage: number;
  currentHealth: number;
  healShieldPower: number;
  healthRegenRate: number;
  lifesteal: number;
  magicLethality: number;
  magicPenetrationFlat: number;
  magicPenetrationPercent: number;
  magicResist: number;
  maxHealth: number;
  moveSpeed: number;
  omnivamp: number;
  physicalLethality: number;
  physicalVamp: number;
  resourceMax: number;
  resourceRegenRate: number;
  resourceType: string; // 'MANA' | 'ENERGY' | 'NONE' 등
  resourceValue: number;
  spellVamp: number;
  tenacity: number;
}

export interface RuneInfo {
  displayName: string;
  id: number;
  rawDescription: string;
  rawDisplayName: string;
}

export interface AbilityInfo {
  abilityLevel: number;
  displayName: string;
  id: string;
  rawDescription: string;
  rawDisplayName: string;
}

// ---------- /liveclientdata/activeplayer ----------
export interface ActivePlayer {
  abilities: {
    Passive: { displayName: string; id: string; rawDescription: string; rawDisplayName: string };
    Q: AbilityInfo;
    W: AbilityInfo;
    E: AbilityInfo;
    R: AbilityInfo;
  };
  championStats: ChampionStats;
  currentGold: number;
  fullRunes: {
    keystone: RuneInfo;
    primaryRuneTree: RuneInfo;
    secondaryRuneTree: RuneInfo;
    generalRunes: RuneInfo[];
    statRunes: { id: number; rawDescription: string }[];
  };
  level: number;
  summonerName: string; // 라이엇 ID 전환 전 호환 필드
  riotId?: string; // 'GameName#TAG'
  riotIdGameName?: string;
  riotIdTagLine?: string;
  teamRelativeColors?: boolean;
}

// ---------- /liveclientdata/playerlist ----------
export interface LiveItem {
  itemID: number;
  displayName: string; // 클라이언트 언어로 번역됨
  rawDisplayName: string;
  rawDescription: string;
  count: number;
  slot: number; // 0~6 (6번이 장신구)
  price: number;
  canUse: boolean;
  consumable: boolean;
}

export interface LivePlayer {
  championName: string; // 클라이언트 언어 (한국 클라면 '가렌')
  rawChampionName: string; // 'game_character_displayname_Garen'
  isBot: boolean;
  isDead: boolean;
  respawnTimer: number;
  items: LiveItem[];
  level: number;
  position: '' | 'TOP' | 'JUNGLE' | 'MIDDLE' | 'BOTTOM' | 'UTILITY';
  runes: {
    keystone: RuneInfo;
    primaryRuneTree: RuneInfo;
    secondaryRuneTree: RuneInfo;
  };
  scores: {
    assists: number;
    creepScore: number;
    deaths: number;
    kills: number;
    wardScore: number;
  };
  skinID: number;
  summonerName: string;
  riotId?: string;
  riotIdGameName?: string;
  riotIdTagLine?: string;
  summonerSpells: {
    summonerSpellOne: { displayName: string; rawDescription: string; rawDisplayName: string };
    summonerSpellTwo: { displayName: string; rawDescription: string; rawDisplayName: string };
  };
  team: 'ORDER' | 'CHAOS'; // ORDER=블루, CHAOS=레드
}
