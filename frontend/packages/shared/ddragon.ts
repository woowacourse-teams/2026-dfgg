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

export interface DDragonData {
  version: string;
  /** championId(숫자) → 챔피언 정보. 밴픽 API가 숫자 id를 준다. */
  byChampionId: Map<number, ChampionInfo>;
  /** 한글명 → 챔피언 정보. 인게임 API는 이름만 준다. */
  byName: Map<string, ChampionInfo>;
}

export function itemImageUrl(version: string, id: number): string {
  return `${DDRAGON}/cdn/${version}/img/item/${id}.png`;
}

export function championImageUrl(version: string, riotKey: string): string {
  return `${DDRAGON}/cdn/${version}/img/champion/${riotKey}.png`;
}

/**
 * 최신 버전과 챔피언 목록을 받아 championId로 찾을 수 있는 형태로 만든다.
 * LCU는 챔피언을 숫자 id로만 알려주므로 이 변환이 필요하다.
 */
export async function loadDDragon(signal?: AbortSignal): Promise<DDragonData> {
  const versionRes = await fetch(`${DDRAGON}/api/versions.json`, { signal });
  if (!versionRes.ok) throw new Error(`versions.json ${versionRes.status}`);
  const [version]: string[] = await versionRes.json();

  const listRes = await fetch(`${DDRAGON}/cdn/${version}/data/${LOCALE}/champion.json`, { signal });
  if (!listRes.ok) throw new Error(`champion.json ${listRes.status}`);
  const { data }: { data: Record<string, DDragonChampion> } = await listRes.json();

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

  return { version, byChampionId, byName };
}
