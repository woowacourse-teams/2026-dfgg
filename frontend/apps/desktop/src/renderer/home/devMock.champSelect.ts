/**
 * 밴픽 화면 mock. devMock 에서만 불러서 배포 빌드에는 들어가지 않는다.
 *
 *   /home/index.html?phase=ChampSelect             상대 3명 공개, 오리아나 고름 (기본)
 *   /home/index.html?phase=ChampSelect&cs=blue     블루 1픽: 상대 공개 전, 챔피언 고르기 전
 *   /home/index.html?phase=ChampSelect&cs=one      상대 1명 공개
 *   /home/index.html?phase=ChampSelect&cs=offlane  공개된 상대가 내 라인이 아님(정글 비에고)
 *   /home/index.html?phase=ChampSelect&cs=locked   내 챔피언 확정 후
 *   /home/index.html?phase=ChampSelect&cs=few      이번 패치 판 수 부족
 *   /home/index.html?phase=ChampSelect&cs=noslot   룬 페이지 칸이 꽉 참
 *
 * 응답 모양은 백엔드 문서 예시를 따른다. 그림 주소는 CommunityDragon 으로 대신 채웠다.
 */
import type {
  ChampSelectState,
  CounterEnemy,
  Position,
  RecommendedRunePage,
  RuneEntry,
  TodayPick,
} from '../../shared/types';

const CDRAGON =
  'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default';
const ITEM = 1_000_000;
const SPELL = 2_000_000;

const PATHS: Record<number, string> = {
  8229: '/v1/perk-images/styles/sorcery/arcanecomet/arcanecomet.png',
  8226: '/v1/perk-images/styles/sorcery/manaflowband/manaflowband.png',
  8210: '/v1/perk-images/styles/sorcery/transcendence/transcendence.png',
  8237: '/v1/perk-images/styles/sorcery/scorch/scorch.png',
  8304: '/v1/perk-images/styles/inspiration/magicalfootwear/magicalfootwear.png',
  8345: '/v1/perk-images/styles/inspiration/biscuitdelivery/biscuitdelivery.png',
  8369: '/v1/perk-images/styles/inspiration/firststrike/firststrike.png',
  8306: '/v1/perk-images/styles/inspiration/hextechflashtraption/hextechflashtraption.png',
  8347: '/v1/perk-images/styles/inspiration/cosmicinsight/cosmicinsight.png',
  8214: '/v1/perk-images/styles/sorcery/summonaery/summonaery.png',
  8444: '/v1/perk-images/styles/resolve/secondwind/secondwind.png',
  8451: '/v1/perk-images/styles/resolve/overgrowth/overgrowth.png',
  8112: '/v1/perk-images/styles/domination/electrocute/electrocute.png',
  8143: '/v1/perk-images/styles/domination/suddenimpact/suddenimpact.png',
  8140: '/v1/perk-images/styles/domination/grislymementos/grislymementos.png',
  8106: '/v1/perk-images/styles/domination/ultimatehunter/ultimatehunter.png',
  8010: '/v1/perk-images/styles/precision/conqueror/conqueror.png',
  9111: '/v1/perk-images/styles/precision/triumph.png',
  9104: '/v1/perk-images/styles/precision/legendalacrity/legendalacrity.png',
  8299: '/v1/perk-images/styles/sorcery/laststand/laststand.png',
  5008: '/v1/perk-images/statmods/statmodsadaptiveforceicon.png',
  5011: '/v1/perk-images/statmods/statmodshealthscalingicon.png',
  8000: '/v1/perk-images/styles/7201_precision.png',
  8100: '/v1/perk-images/styles/7200_domination.png',
  8200: '/v1/perk-images/styles/7202_sorcery.png',
  8300: '/v1/perk-images/styles/7203_whimsy.png',
  8400: '/v1/perk-images/styles/7204_resolve.png',
  1003020: '/assets/items/icons2d/3020_class_t2_sorcerersshoes.png',
  1003165: '/assets/items/icons2d/3165_mage_t3_morellonomicon.png',
  1003157: '/assets/items/icons2d/3157_mage_t3_zhonyashourglass.png',
  1003089: '/assets/items/icons2d/3089_mage_t3_deathcap.png',
  1003116: '/assets/items/icons2d/3116_mage_t3_rylajscrystalscepter.png',
  1003115: '/assets/items/icons2d/3115_mage_t3_nashorstooth.png',
  2000004: '/data/spells/icons2d/summoner_flash.png',
  2000012: '/data/spells/icons2d/summoner_teleport_new.png',
  2000014: '/data/spells/icons2d/summonerignite.png',
};

const url = (key: number) => (PATHS[key] ? `${CDRAGON}${PATHS[key]}` : null);
const rune = (id: number, name: string): RuneEntry => ({ id, name, imageUrl: url(id) });
const named = (offset: number) => (id: number, name: string) => ({
  id,
  name,
  imageUrl: url(offset + id) ?? '',
});
const item = named(ITEM);
const spell = named(SPELL);

const SORCERY = rune(8200, '마법');
const INSPIRATION = rune(8300, '영감');
const RESOLVE = rune(8400, '결의');
const SHARDS = [rune(5008, '적응형 능력치'), rune(5008, '적응형 능력치'), rune(5011, '체력')];
const COMET = [rune(8226, '마나순환 팔찌'), rune(8210, '깨달음'), rune(8237, '주문 작열')];
const ELECTROCUTE = [
  rune(8143, '돌발 일격'),
  rune(8140, '섬뜩한 기념품'),
  rune(8106, '궁극의 사냥꾼'),
];

const PAGES: RecommendedRunePage[] = [
  {
    rank: 1,
    primaryStyle: SORCERY,
    keystone: rune(8229, '신비로운 유성'),
    primaryRunes: COMET,
    subStyle: INSPIRATION,
    subRunes: [rune(8304, '마법의 신발'), rune(8345, '비스킷 배달')],
    statShards: SHARDS,
    evidence: { games: 608, pickRate: 0.4 },
  },
  {
    rank: 2,
    primaryStyle: INSPIRATION,
    keystone: rune(8369, '선제공격'),
    primaryRunes: [
      rune(8306, '마법공학 점멸기'),
      rune(8345, '비스킷 배달'),
      rune(8347, '우주적 통찰력'),
    ],
    subStyle: SORCERY,
    subRunes: [rune(8226, '마나순환 팔찌'), rune(8237, '주문 작열')],
    statShards: SHARDS,
    evidence: { games: 334, pickRate: 0.22 },
  },
  {
    rank: 3,
    primaryStyle: SORCERY,
    keystone: rune(8214, '콩콩이 소환'),
    primaryRunes: COMET,
    subStyle: RESOLVE,
    subRunes: [rune(8444, '재생의 바람'), rune(8451, '과잉성장')],
    statShards: SHARDS,
    evidence: { games: 213, pickRate: 0.14 },
  },
];

const LEAGUEPEDIA = 'https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/';
const FLASH = spell(4, '점멸');
const TELEPORT = spell(12, '순간이동');
const IGNITE = spell(14, '점화');

function pick(
  name: string,
  team: string,
  photo: string,
  champion: [number, string],
  spells: TodayPick['spells'],
  items: TodayPick['items'],
  electrocute: boolean,
): TodayPick {
  return {
    player: { name, team, imageUrl: `${LEAGUEPEDIA}${photo}` },
    playedAt: '2026-10-16',
    champion: { id: champion[0], name: champion[1], imageUrl: '', position: 'MID' },
    spells,
    items,
    runes: electrocute
      ? {
          primaryTree: { id: 8100, name: '지배' },
          keystone: rune(8112, '감전'),
          primary: ELECTROCUTE,
          secondaryTree: { id: 8200, name: '마법' },
          secondary: [rune(8226, '마나순환 팔찌'), rune(8237, '주문 작열')],
          shards: SHARDS,
        }
      : {
          primaryTree: { id: 8200, name: '마법' },
          keystone: rune(8229, '신비로운 유성'),
          primary: COMET,
          secondaryTree: { id: 8300, name: '영감' },
          secondary: [rune(8304, '마법의 신발'), rune(8345, '비스킷 배달')],
          shards: SHARDS,
        },
  };
}

const BOOTS = item(3020, '마법사의 신발');
const TODAY_PICKS = {
  tournament: {
    name: 'Worlds 2026 Play-In',
    league: 'World Championship',
    startDate: '2026-10-15',
  },
  position: 'MID' as Position,
  picks: [
    pick(
      'Faker',
      'T1',
      '5/5a/T1_Faker_2026_LCK_Cup.png/revision/latest?cb=20260122163312',
      [103, '아리'],
      [FLASH, IGNITE],
      [
        BOOTS,
        item(3165, '모렐로노미콘'),
        item(3157, '존야의 모래시계'),
        item(3089, '라바돈의 죽음모자'),
      ],
      true,
    ),
    pick(
      'Chovy',
      'Gen.G',
      'b/b3/GEN_Chovy_2026_Split_1.png/revision/latest?cb=20260122171212',
      [61, '오리아나'],
      [FLASH, TELEPORT],
      [BOOTS, item(3116, '라일라이의 수정홀'), item(3089, '라바돈의 죽음모자')],
      false,
    ),
    pick(
      'Caps',
      'G2 Esports',
      '6/6a/G2_Caps_2026_Split_1.png/revision/latest?cb=20260117092522',
      [7, '르블랑'],
      [FLASH, IGNITE],
      [BOOTS, item(3157, '존야의 모래시계')],
      true,
    ),
    pick(
      'Knight',
      'BLG',
      'e/ec/BLG_Knight_2026_Split_1.png/revision/latest?cb=20260327160422',
      [268, '아지르'],
      [FLASH, TELEPORT],
      [BOOTS, item(3115, '내셔의 이빨')],
      false,
    ),
    pick(
      'Bdd',
      'KT Rolster',
      'd/d8/KT_Bdd_2026_Split_1.png/revision/latest?cb=20260124034525',
      [163, '탈리야'],
      [FLASH, TELEPORT],
      [BOOTS, item(3165, '모렐로노미콘')],
      false,
    ),
  ],
};

const enemy = (
  championId: number,
  name: string,
  positions: [Position, number][],
  counters: [number, string][],
): CounterEnemy => ({
  championId,
  name,
  positions: positions.map(([position, rate]) => ({ position, rate })),
  counters: counters.map(([id, counterName]) => ({ championId: id, name: counterName })),
});

const ARI = enemy(
  103,
  '아리',
  [
    ['MID', 91.9],
    ['TOP', 8.1],
    ['JUNGLE', 0],
    ['BOTTOM', 0],
    ['SUPPORT', 0],
  ],
  [
    [131, '다이애나'],
    [55, '카타리나'],
    [246, '키아나'],
    [800, '멜'],
    [711, '벡스'],
  ],
);
const ZED = enemy(
  238,
  '제드',
  [
    ['MID', 63.9],
    ['JUNGLE', 19.4],
    ['TOP', 16.7],
    ['BOTTOM', 0],
    ['SUPPORT', 0],
  ],
  [
    [245, '에코'],
    [127, '리산드라'],
    [105, '피즈'],
    [142, '조이'],
    [39, '이렐리아'],
  ],
);
const VIEGO = enemy(
  234,
  '비에고',
  [
    ['JUNGLE', 88.2],
    ['MID', 6.1],
    ['TOP', 5.7],
    ['BOTTOM', 0],
    ['SUPPORT', 0],
  ],
  [],
);

const counters = (enemies: CounterEnemy[]) => ({
  patch: '16.20',
  myPosition: 'MID' as Position,
  enemies,
});
const RUNES = { patch: '16.21', championGames: 1520, pages: PAGES };

const BASE: ChampSelectState = {
  position: 'MID',
  myChampionId: 61,
  locked: false,
  todayPicks: TODAY_PICKS,
  counters: counters([ARI, ZED, VIEGO]),
  runes: RUNES,
  applied: 1,
  runeError: null,
};

const SCENARIOS: Record<string, Partial<ChampSelectState>> = {
  picked: {},
  blue: { myChampionId: null, counters: counters([]), runes: null, applied: null },
  one: { counters: counters([ARI]) },
  offlane: { counters: counters([VIEGO]) },
  locked: { locked: true },
  few: { runes: { patch: '16.21', championGames: 60, pages: [] }, applied: null },
  noslot: { applied: null, runeError: 'no-slot' },
};

export function champSelectMock(scenario: string | null): ChampSelectState {
  return { ...BASE, ...(SCENARIOS[scenario ?? 'picked'] ?? {}) };
}
