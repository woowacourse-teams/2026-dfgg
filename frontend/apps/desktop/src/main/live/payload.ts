import type { LivePlayer } from './types';
import { trackEvent } from '../analytics/umami';

export interface ChampionEntry {
  name: string;
  position: string;
}

export interface InventoryItem {
  itemId: number;
  quantity: number;
}

export interface RecommendationBody {
  myChampion: ChampionEntry;
  purchasedItemIds: number[];
  allies: ChampionEntry[];
  enemies: ChampionEntry[];
  tier: string;
  patch: string;
  riotId: string;
  /** 지금 인벤토리의 모든 아이템과 수량 (시작 아이템, 재료, 완성 아이템, 장신구 포함) */
  inventoryItems: InventoryItem[];
  availableGold: number;
  gameTimeMs: number;
}

interface BuildParams {
  players: LivePlayer[];
  myRiotId: string;
  patch: string;
  purchasedItemIds: Set<number>;
  championNames?: Record<string, string>;
  availableGold: number;
  gameTimeMs: number;
}

// Live API 는 MIDDLE/UTILITY, 백엔드는 MID/SUPPORT 를 쓴다.
const POSITION_MAP: Record<string, string> = {
  TOP: 'TOP',
  JUNGLE: 'JUNGLE',
  MIDDLE: 'MID',
  BOTTOM: 'BOTTOM',
  UTILITY: 'SUPPORT',
};

const TRINKET_SLOT = 6;

// 챔피언마다 원본 이름 형식이 달라서 ddragon 표 확인이 실패했을 때만 쓴다.
// 'game_character_displayname_Garen' -> 'Garen'
// 'Character_Seraphine_Name' -> 'Seraphine'
const RAW_CHAMPION_NAME_PATTERNS = [/^game_character_displayname_(\w+)$/, /^Character_(\w+)_Name$/];

// 형식에 기대지 않도록 '_' 로 나눈 조각 중 ddragon 챔피언 id 와 같은 것을 먼저 찾는다.
function toChampionAlias(rawChampionName: string, championNames?: Record<string, string>) {
  const known = rawChampionName.split('_').find((part) => championNames?.[part]);
  if (known) return known;

  for (const pattern of RAW_CHAMPION_NAME_PATTERNS) {
    const match = rawChampionName.match(pattern);
    if (match) return match[1];
  }
  return rawChampionName;
}

// 'LeeSin' -> 'Lee Sin' (표에 없는 챔피언을 위한 대략적인 폴백)
function splitPascalCase(alias: string) {
  return alias.replace(/([a-z])([A-Z])/g, '$1 $2');
}

// ddragon에서 영어 이름 찾기
// 표에 없어 이름을 추측한 원본 이름. 2초마다 10명을 변환하므로 같은 이름은 한 번만 보낸다.
const reportedGuesses = new Set<string>();

function toEnglishName(rawChampionName: string, championNames?: Record<string, string>) {
  const alias = toChampionAlias(rawChampionName, championNames);
  const known = championNames?.[alias];
  if (known) return known;

  // 신규 챔피언이나 이름 형식이 바뀌면 여기로 온다. 틀린 이름이 에러 없이 백엔드로 가서 따로 알린다.
  const guessed = splitPascalCase(alias);
  if (championNames && !reportedGuesses.has(rawChampionName)) {
    reportedGuesses.add(rawChampionName);
    trackEvent('desktop-error', { type: 'champion-name', raw: rawChampionName, guessed });
  }
  return guessed;
}

// 포지션 찾기
function toPosition(position: string) {
  return POSITION_MAP[position] ?? position;
}

// 플레이어 정보를 받아오기
function toEntry(player: LivePlayer, championNames?: Record<string, string>): ChampionEntry {
  return {
    name: toEnglishName(player.rawChampionName, championNames),
    position: toPosition(player.position),
  };
}

// 장신구·소모품(와드, 물약)과 재료 아이템은 제외한다.
// componentItemIds 에 없는 id 는 통과시킨다 (신규 아이템이 누락되지 않도록).
export function extractItemIds(player: LivePlayer, componentItemIds: Set<number>) {
  return player.items
    .filter((item) => !item.consumable && item.slot !== TRINKET_SLOT)
    .filter((item) => !componentItemIds.has(item.itemID))
    .map((item) => item.itemID);
}

/**
 * 인벤토리를 백엔드 형식으로 바꾼다. 같은 아이템이 여러 칸에 있으면 수량으로 합친다.
 * 같은 itemId 가 두 번 나가면 백엔드가 400 을 돌려준다.
 */
export function toInventory(player: LivePlayer): InventoryItem[] {
  const quantities = new Map<number, number>();
  for (const { itemID, count } of player.items) {
    quantities.set(itemID, (quantities.get(itemID) ?? 0) + Math.max(1, count));
  }
  return [...quantities.entries()]
    .sort(([a], [b]) => a - b)
    .map(([itemId, quantity]) => ({ itemId, quantity }));
}

// '15.19.700.1234' -> '15.19'
function toPatchVersion(gameVersion: string) {
  return gameVersion.split('.').slice(0, 2).join('.');
}

// 라이브 플레이어들 중에서 나를 찾기
function isMe(player: LivePlayer, myRiotId: string) {
  return player.riotId === myRiotId || player.summonerName === myRiotId;
}

// 벡엔드 요청 body에 보낼 함수
export function buildRecommendationBody({
  players,
  myRiotId,
  patch,
  championNames,
  purchasedItemIds,
  availableGold,
  gameTimeMs,
}: BuildParams): RecommendationBody | null {
  const me = players.find((player) => isMe(player, myRiotId));
  if (!me) return null;

  const allies = players.filter((player) => player.team === me.team && !isMe(player, myRiotId));
  const enemies = players.filter((player) => player.team !== me.team);

  return {
    myChampion: toEntry(me, championNames),
    purchasedItemIds: [...purchasedItemIds],
    allies: allies.map((player) => toEntry(player, championNames)),
    enemies: enemies.map((player) => toEntry(player, championNames)),
    tier: 'PLATINUM',
    patch: toPatchVersion(patch),
    riotId: myRiotId,
    inventoryItems: toInventory(me),
    // 백엔드는 0 이상의 정수만 받는다. Live API 골드는 소수점까지 온다.
    availableGold: Math.max(0, Math.floor(availableGold)),
    gameTimeMs: Math.max(0, Math.round(gameTimeMs)),
  };
}
