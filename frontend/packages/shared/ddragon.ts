const DDRAGON = 'https://ddragon.leagueoflegends.com';
const LOCALE = 'ko_KR';

export interface ChampionInfo {
  /** Riot 영문 키. 백엔드에 보내는 값이다. (예: MonkeyKing) */
  riotKey: string;
  /** 화면에 보여줄 한글명 (예: 손오공) */
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
  /** 한글명 → 챔피언 정보. 인게임 API는 이름만 준다. */
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

/**
 * 최신 버전과 챔피언 목록을 받아 championId로 찾을 수 있는 형태로 만든다.
 * LCU는 챔피언을 숫자 id로만 알려주므로 이 변환이 필요하다.
 */
export async function loadDDragon(signal?: AbortSignal): Promise<DDragonData> {
  const versionRes = await fetch(`${DDRAGON}/api/versions.json`, { signal });
  if (!versionRes.ok) throw new Error(`versions.json ${versionRes.status}`);
  const [version]: string[] = await versionRes.json();

  const [championRes, itemRes] = await Promise.all([
    fetch(`${DDRAGON}/cdn/${version}/data/${LOCALE}/champion.json`, { signal }),
    fetch(`${DDRAGON}/cdn/${version}/data/${LOCALE}/item.json`, { signal }),
  ]);
  if (!championRes.ok) throw new Error(`champion.json ${championRes.status}`);
  if (!itemRes.ok) throw new Error(`item.json ${itemRes.status}`);

  const { data }: { data: Record<string, DDragonChampion> } = await championRes.json();

  const byChampionId = new Map<number, ChampionInfo>();
  const byName = new Map<string, ChampionInfo>();

  for (const key of Object.keys(data)) {
    // Jade_* 는 별도 모드용 중복 항목이다. 본편 챔피언만 남긴다.
    if (key.indexOf('_') !== -1) continue;
    const champion = data[key];
    const info: ChampionInfo = {
      riotKey: champion.id,
      name: champion.name,
      imageUrl: championImageUrl(version, champion.id),
    };
    byChampionId.set(Number(champion.key), info);
    // 인게임 API가 주는 이름과 맞추기 위해 공백을 지운 형태로도 넣어둔다.
    byName.set(champion.name, info);
    byName.set(champion.name.replace(/\s/g, ''), info);
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
