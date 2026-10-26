/**
 * 시안에 쓰는 가짜 데이터. 백엔드 문서의 예시 응답 모양을 그대로 따른다.
 * - 룬 추천: POST /api/recommendations/runes
 * - 오늘의 픽: POST /api/today-picks
 * 그림 주소(imageUrl)는 시안에서 CommunityDragon 으로 대신 그려서 뺐다.
 */

export type Named = { id: number; name: string };

export type RunePage = {
  rank: number;
  primaryStyle: Named;
  keystone: Named;
  primaryRunes: Named[];
  subStyle: Named;
  subRunes: Named[];
  statShards: Named[];
  evidence: { games: number; pickRate: number };
};

export type TodayPick = {
  player: { name: string; team: string | null; imageUrl: string | null };
  playedAt: string;
  champion: Named;
  spells: Named[];
  items: Named[];
  runes: {
    primaryTree: Named;
    keystone: Named;
    primary: Named[];
    secondaryTree: Named;
    secondary: Named[];
    shards: Named[];
  };
};

/** 밴픽에서 내가 고른 챔피언과 포지션. LCU 챔피언 선택 세션에서 읽는다. */
export const ME = { champion: { id: 61, name: '오리아나' }, position: '미드' };

const SORCERY = { id: 8200, name: '마법' };
const INSPIRATION = { id: 8300, name: '영감' };
const RESOLVE = { id: 8400, name: '결의' };
const DOMINATION = { id: 8100, name: '지배' };
const PRECISION = { id: 8000, name: '정밀' };

const SHARDS_AP = [
  { id: 5008, name: '적응형 능력치' },
  { id: 5008, name: '적응형 능력치' },
  { id: 5011, name: '체력' },
];

export const RUNES = {
  patch: '16.21',
  championGames: 1520,
  pages: [
    {
      rank: 1,
      primaryStyle: SORCERY,
      keystone: { id: 8229, name: '신비로운 유성' },
      primaryRunes: [
        { id: 8226, name: '마나순환 팔찌' },
        { id: 8210, name: '깨달음' },
        { id: 8237, name: '주문 작열' },
      ],
      subStyle: INSPIRATION,
      subRunes: [
        { id: 8304, name: '마법의 신발' },
        { id: 8345, name: '비스킷 배달' },
      ],
      statShards: SHARDS_AP,
      evidence: { games: 608, pickRate: 0.4 },
    },
    {
      rank: 2,
      primaryStyle: INSPIRATION,
      keystone: { id: 8369, name: '선제공격' },
      primaryRunes: [
        { id: 8306, name: '마법공학 점멸기' },
        { id: 8345, name: '비스킷 배달' },
        { id: 8347, name: '우주적 통찰력' },
      ],
      subStyle: SORCERY,
      subRunes: [
        { id: 8226, name: '마나순환 팔찌' },
        { id: 8237, name: '주문 작열' },
      ],
      statShards: SHARDS_AP,
      evidence: { games: 334, pickRate: 0.22 },
    },
    {
      rank: 3,
      primaryStyle: SORCERY,
      keystone: { id: 8214, name: '콩콩이 소환' },
      primaryRunes: [
        { id: 8226, name: '마나순환 팔찌' },
        { id: 8210, name: '깨달음' },
        { id: 8237, name: '주문 작열' },
      ],
      subStyle: RESOLVE,
      subRunes: [
        { id: 8444, name: '재생의 바람' },
        { id: 8451, name: '과잉성장' },
      ],
      statShards: SHARDS_AP,
      evidence: { games: 213, pickRate: 0.14 },
    },
  ] satisfies RunePage[],
};

const FLASH = { id: 4, name: '점멸' };
const TELEPORT = { id: 12, name: '순간이동' };
const IGNITE = { id: 14, name: '점화' };

const pick = (
  player: TodayPick['player'],
  playedAt: string,
  champion: Named,
  spells: Named[],
  items: Named[],
  runes: TodayPick['runes'],
): TodayPick => ({ player, playedAt, champion, spells, items, runes });

// 선수 사진은 백엔드처럼 Leaguepedia 이미지 직접 링크. 2026 시즌 프로필 사진을 썼다.
export const TODAY = {
  tournament: {
    name: 'Worlds 2026 Play-In',
    league: 'World Championship',
    startDate: '2026-10-15',
  },
  position: 'MID',
  picks: [
    pick(
      {
        name: 'Faker',
        team: 'T1',
        imageUrl:
          'https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/5/5a/T1_Faker_2026_LCK_Cup.png/revision/latest?cb=20260122163312',
      },
      '2026-10-16',
      { id: 103, name: '아리' },
      [FLASH, IGNITE],
      [
        { id: 3020, name: '마법사의 신발' },
        { id: 3165, name: '모렐로노미콘' },
        { id: 3157, name: '존야의 모래시계' },
        { id: 3089, name: '라바돈의 죽음모자' },
      ],
      {
        primaryTree: DOMINATION,
        keystone: { id: 8112, name: '감전' },
        primary: [
          { id: 8143, name: '돌발 일격' },
          { id: 8140, name: '섬뜩한 기념품' },
          { id: 8106, name: '궁극의 사냥꾼' },
        ],
        secondaryTree: SORCERY,
        secondary: [
          { id: 8226, name: '마나순환 팔찌' },
          { id: 8237, name: '주문 작열' },
        ],
        shards: SHARDS_AP,
      },
    ),
    pick(
      {
        name: 'Chovy',
        team: 'Gen.G',
        imageUrl:
          'https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/b/b3/GEN_Chovy_2026_Split_1.png/revision/latest?cb=20260122171212',
      },
      '2026-10-16',
      { id: 61, name: '오리아나' },
      [FLASH, TELEPORT],
      [
        { id: 3020, name: '마법사의 신발' },
        { id: 3116, name: '라일라이의 수정홀' },
        { id: 3089, name: '라바돈의 죽음모자' },
      ],
      {
        primaryTree: SORCERY,
        keystone: { id: 8229, name: '신비로운 유성' },
        primary: [
          { id: 8226, name: '마나순환 팔찌' },
          { id: 8210, name: '깨달음' },
          { id: 8237, name: '주문 작열' },
        ],
        secondaryTree: INSPIRATION,
        secondary: [
          { id: 8304, name: '마법의 신발' },
          { id: 8345, name: '비스킷 배달' },
        ],
        shards: SHARDS_AP,
      },
    ),
    pick(
      {
        name: 'Caps',
        team: 'G2 Esports',
        imageUrl:
          'https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/6/6a/G2_Caps_2026_Split_1.png/revision/latest?cb=20260117092522',
      },
      '2026-10-15',
      { id: 7, name: '르블랑' },
      [FLASH, IGNITE],
      [
        { id: 3020, name: '마법사의 신발' },
        { id: 3157, name: '존야의 모래시계' },
      ],
      {
        primaryTree: DOMINATION,
        keystone: { id: 8112, name: '감전' },
        primary: [
          { id: 8143, name: '돌발 일격' },
          { id: 8140, name: '섬뜩한 기념품' },
          { id: 8106, name: '궁극의 사냥꾼' },
        ],
        secondaryTree: INSPIRATION,
        secondary: [
          { id: 8304, name: '마법의 신발' },
          { id: 8347, name: '우주적 통찰력' },
        ],
        shards: SHARDS_AP,
      },
    ),
    pick(
      {
        name: 'Knight',
        team: 'BLG',
        imageUrl:
          'https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/e/ec/BLG_Knight_2026_Split_1.png/revision/latest?cb=20260327160422',
      },
      '2026-10-15',
      { id: 268, name: '아지르' },
      [FLASH, TELEPORT],
      [
        { id: 3020, name: '마법사의 신발' },
        { id: 3115, name: '내셔의 이빨' },
        { id: 3089, name: '라바돈의 죽음모자' },
      ],
      {
        primaryTree: PRECISION,
        keystone: { id: 8010, name: '정복자' },
        primary: [
          { id: 9111, name: '승전보' },
          { id: 9104, name: '전설: 민첩함' },
          { id: 8299, name: '최후의 저항' },
        ],
        secondaryTree: SORCERY,
        secondary: [
          { id: 8226, name: '마나순환 팔찌' },
          { id: 8237, name: '주문 작열' },
        ],
        shards: SHARDS_AP,
      },
    ),
    pick(
      {
        name: 'Bdd',
        team: 'KT Rolster',
        imageUrl:
          'https://static.wikia.nocookie.net/lolesports_gamepedia_en/images/d/d8/KT_Bdd_2026_Split_1.png/revision/latest?cb=20260124034525',
      },
      '2026-10-15',
      { id: 163, name: '탈리야' },
      [FLASH, TELEPORT],
      [
        { id: 3020, name: '마법사의 신발' },
        { id: 3165, name: '모렐로노미콘' },
      ],
      {
        primaryTree: SORCERY,
        keystone: { id: 8229, name: '신비로운 유성' },
        primary: [
          { id: 8226, name: '마나순환 팔찌' },
          { id: 8210, name: '깨달음' },
          { id: 8237, name: '주문 작열' },
        ],
        secondaryTree: INSPIRATION,
        secondary: [
          { id: 8304, name: '마법의 신발' },
          { id: 8345, name: '비스킷 배달' },
        ],
        shards: SHARDS_AP,
      },
    ),
  ],
};

/** '2026-10-16' → '10.16' */
export const shortDate = (date: string): string => date.slice(5).replace('-', '.');

/** 0.4 → '40%' */
export const percent = (rate: number): string => `${Math.round(rate * 100)}%`;

export type Position = 'TOP' | 'JUNGLE' | 'MID' | 'BOTTOM' | 'SUPPORT';

export type CounterEnemy = Named & {
  championId: number;
  positions: { position: Position; rate: number }[];
  counters: { championId: number; name: string }[];
};

export const POSITION_LABEL: Record<Position, string> = {
  TOP: '탑',
  JUNGLE: '정글',
  MID: '미드',
  BOTTOM: '원딜',
  SUPPORT: '서폿',
};

const enemy = (
  championId: number,
  name: string,
  positions: [Position, number][],
  counters: [number, string][],
): CounterEnemy => ({
  id: championId,
  championId,
  name,
  positions: positions.map(([position, rate]) => ({ position, rate })),
  counters: counters.map(([id, counterName]) => ({ championId: id, name: counterName })),
});

/**
 * POST /api/counters 응답. 상대가 챔피언을 하나 공개할 때마다 다시 부른다.
 * 비에고는 정글 챔피언이라 미드 카운터 데이터가 없는 경우(빈 목록)를 같이 본다.
 */
export const COUNTERS = {
  patch: '16.20',
  myPosition: 'MID' as Position,
  enemies: [
    enemy(
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
    ),
    enemy(
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
    ),
    enemy(
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
    ),
  ],
};

/** 내 포지션으로 올 확률. 상대 픽 중 누가 맞라인일지 가늠한다. */
export const rateAt = (target: CounterEnemy, position: Position): number =>
  target.positions.find((entry) => entry.position === position)?.rate ?? 0;
