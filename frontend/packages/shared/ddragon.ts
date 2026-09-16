import { DDRAGON_LOCALE, type Lang } from '../i18n/lang';

const DDRAGON = 'https://ddragon.leagueoflegends.com';

/**
 * 인게임 API는 롤 클라이언트 언어로 챔피언 이름을 준다. UI 언어와는 무관하다.
 * 그래서 이름 → 챔피언 조회표는 UI 언어와 상관없이 항상 두 언어를 다 채운다.
 * 이걸 UI 언어만 따라가게 두면, 한국 사용자가 UI를 영어로 바꾼 순간 인게임
 * 추천이 조용히 멈춘다.
 */
const NAME_LOOKUP_LOCALES = ['ko_KR', 'en_US'] as const;

export interface ChampionInfo {
  /** Riot 영문 키. 백엔드에 보내는 값이다. (예: MonkeyKing) */
  riotKey: string;
  /** 화면에 보여줄 이름. 선택한 UI 언어를 따른다. (예: 손오공 / Wukong) */
  name: string;
  imageUrl: string;
}

interface DDragonChampion {
  id: string;
  /** LCU가 주는 숫자 championId와 같은 값. 문자열로 온다. */
  key: string;
  name: string;
}

interface DDragonItem {
  /** 전설급 이상 완성 아이템에만 있다. 재료템·소모품·트린켓엔 아예 없는 필드다. */
  depth?: number;
  /** 이 아이템을 재료로 더 업그레이드할 수 있는 다음 아이템 id들. */
  into?: string[];
}

export interface DDragonData {
  version: string;
  /** championId(숫자) → 챔피언 정보. 밴픽 API가 숫자 id를 준다. */
  byChampionId: Map<number, ChampionInfo>;
  /**
   * 이름 → 챔피언 정보. 인게임 API는 이름만 주므로 한글명·영문명을 모두 넣는다.
   * UI 언어를 바꿔도 이 표는 그대로다.
   */
  byName: Map<string, ChampionInfo>;
  /** 전설급 이상이면서 더 이상 업그레이드할 데가 없는 진짜 완성 아이템 id 집합. */
  coreItemIds: Set<number>;
}

export function itemImageUrl(version: string, id: number): string {
  return `${DDRAGON}/cdn/${version}/img/item/${id}.png`;
}

/**
 * 아레나·아람 같은 모드는 같은 아이템을 다른 숫자 id로 보고한다.
 * 예: 광전사의 군화 = 3006(일반) / 223006(아레나) / 773006(구 아람) — 이름·아이콘은
 * 완전히 같은데 id만 다르다. Data Dragon의 item.json을 보면 전부
 * "2자리 접두어 + 원래 4자리 id" 패턴이라, 접두어를 떼면 원래 id로 돌아간다.
 *
 * 인게임에서 보고되는 소유 아이템 id와 추천 빌드의 id를 비교할 때 이 변환 없이
 * 그냥 ===로 비교하면, 모드 전용 id로 산 아이템은 같은 아이템인데도 구매 체크가
 * 안 붙는다.
 */
export function canonicalItemId(id: number): number {
  const digits = String(id);
  if (digits.length === 6) {
    const base = Number(digits.slice(2));
    if (base >= 1000 && base <= 9999) return base;
  }
  return id;
}

export function championImageUrl(version: string, riotKey: string): string {
  return `${DDRAGON}/cdn/${version}/img/champion/${riotKey}.png`;
}

/**
 * Data Dragon 버전(예: "16.17.1")을 실제 패치 번호("16.17")로 줄인다.
 * 마지막 자리는 데이터 갱신 차수일 뿐 패치와는 무관하다.
 */
export function toPatch(version: string): string {
  return version.split('.').slice(0, 2).join('.');
}

/** champion.json 한 판을 받아 key → 챔피언 형태로 돌려준다. */
async function fetchChampions(
  version: string,
  locale: string,
  signal?: AbortSignal,
): Promise<Record<string, DDragonChampion>> {
  const res = await fetch(`${DDRAGON}/cdn/${version}/data/${locale}/champion.json`, { signal });
  if (!res.ok) throw new Error(`champion.json ${locale} ${res.status}`);
  const { data }: { data: Record<string, DDragonChampion> } = await res.json();
  return data;
}

/**
 * 최신 버전과 챔피언 목록을 받아 championId로 찾을 수 있는 형태로 만든다.
 * LCU는 챔피언을 숫자 id로만 알려주므로 이 변환이 필요하다.
 *
 * 화면에 쓸 이름은 lang 을 따르지만, byName 조회표는 항상 한국어·영어를 모두
 * 채운다 — 인게임 API가 주는 이름은 롤 클라이언트 언어라 UI 언어와 다를 수 있다.
 */
export async function loadDDragon(lang: Lang = 'ko', signal?: AbortSignal): Promise<DDragonData> {
  const versionRes = await fetch(`${DDRAGON}/api/versions.json`, { signal });
  if (!versionRes.ok) throw new Error(`versions.json ${versionRes.status}`);
  const [version]: string[] = await versionRes.json();

  const displayLocale = DDRAGON_LOCALE[lang];
  // 표시용 로케일이 조회용 두 로케일 중 하나면 중복해서 받지 않는다.
  const locales = [
    displayLocale,
    ...NAME_LOOKUP_LOCALES.filter((locale) => locale !== displayLocale),
  ];

  const [itemRes, ...championData] = await Promise.all([
    fetch(`${DDRAGON}/cdn/${version}/data/${displayLocale}/item.json`, { signal }),
    ...locales.map((locale) => fetchChampions(version, locale, signal)),
  ]);
  if (!itemRes.ok) throw new Error(`item.json ${itemRes.status}`);

  // locales[0] 이 표시용이므로 첫 응답이 화면에 쓸 이름을 들고 있다.
  const [display, ...lookups] = championData;

  const byChampionId = new Map<number, ChampionInfo>();
  const byName = new Map<string, ChampionInfo>();

  for (const key of Object.keys(display)) {
    // Jade_* 는 별도 모드용 중복 항목이다. 본편 챔피언만 남긴다.
    if (key.indexOf('_') !== -1) continue;
    const champion = display[key];
    const info: ChampionInfo = {
      riotKey: champion.id,
      name: champion.name,
      imageUrl: championImageUrl(version, champion.id),
    };
    byChampionId.set(Number(champion.key), info);

    // 표시용 이름과, 다른 언어로 오는 인게임 이름을 모두 같은 챔피언으로 잇는다.
    // 인게임 API가 주는 이름과 맞추기 위해 공백을 지운 형태로도 넣어둔다.
    for (const source of [display, ...lookups]) {
      const alias = source[key]?.name;
      if (!alias) continue;
      byName.set(alias, info);
      byName.set(alias.replace(/\s/g, ''), info);
    }
  }

  const { data: itemData }: { data: Record<string, DDragonItem> } = await itemRes.json();
  const coreItemIds = new Set<number>();
  for (const key of Object.keys(itemData)) {
    const item = itemData[key];
    // depth만 보면 점화석처럼 "전설급 가격이지만 계속 다른 완성템으로 이어지는"
    // 재료템까지 걸린다. into가 남아있다는 건 아직 업그레이드할 데가 있다는
    // 뜻이라, 더 갈 곳이 없는 것만 진짜 완성 아이템으로 본다.
    if (item.depth === undefined) continue;
    if (item.into && item.into.length > 0) continue;
    // 아레나·아람 전용 id로 온 것도 같은 아이템으로 잡히게 원래 id로 정규화해서 넣는다.
    coreItemIds.add(canonicalItemId(Number(key)));
  }

  return { version, byChampionId, byName, coreItemIds };
}
