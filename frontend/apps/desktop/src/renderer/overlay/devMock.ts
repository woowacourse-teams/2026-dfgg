/**
 * 브라우저에서 overlay 화면을 바로 열어 볼 때 쓰는 가짜 preload API.
 * 개발 서버에서만 번들에 들어간다(webpack.config.js).
 *
 * 주소에 값을 붙여 상태를 바꾼다.
 *   /overlay/index.html                 게임 중 · 추천 5개 (기본)
 *   /overlay/index.html?state=waiting   게임 중 · 추천을 기다리는 중
 *   /overlay/index.html?state=idle      게임 밖
 *   /overlay/index.html?bought=4        산 아이템 수 (0~5)
 *   /overlay/index.html?situ=0          상황템 개수 (0~2, 기본 2). 코어와 합쳐 5줄이 되는지 볼 때
 */
import type { NamedEntry, RecommendedItem, SituationalItem } from '../../shared/types';

const params = new URLSearchParams(window.location.search);
const state = params.get('state') ?? 'ready';
const DEFAULT_BOUGHT = 2;
const bought = Number(params.get('bought') ?? DEFAULT_BOUGHT);
const MAX_SITUATIONAL = 2;
const situCount = Math.min(MAX_SITUATIONAL, Number(params.get('situ') ?? MAX_SITUATIONAL));
const MOCK_GOLD = 1200;

const championIcon = (id: number) =>
  `https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons/${id}.png`;

const champion = (id: number, name: string): NamedEntry => ({
  id,
  name,
  imageUrl: championIcon(id),
});

const CDRAGON_DATA =
  'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default';

type CdragonItem = { id: number; iconPath: string };

/**
 * 실제 앱에서는 백엔드가 아이템 그림 주소를 준다. 여기서는 CommunityDragon 의 아이템 목록에서 찾는다.
 * 목록의 경로('/lol-game-data/assets/ASSETS/Items/...')는 소문자로 바꿔 기본 주소 뒤에 붙이면 그림 주소가 된다.
 */
const itemIcons: Promise<Map<number, string>> = fetch(`${CDRAGON_DATA}/v1/items.json`)
  .then((response) => response.json())
  .then(
    (items: CdragonItem[]) =>
      new Map(
        items.map(({ id, iconPath }) => [
          id,
          `${CDRAGON_DATA}${iconPath.toLowerCase().replace('/lol-game-data/assets', '')}`,
        ]),
      ),
  )
  // 목록을 못 받아도 화면은 떠야 한다. 그림만 빈 채로 둔다.
  .catch(() => new Map<number, string>());

type Seed = Pick<RecommendedItem, 'id' | 'name' | 'description'> & {
  /** [재료 id, 이름, 수량] 을 사는 순서대로. 없으면 지금 살 묶음이 없는 코어 */
  bundle?: { items: [number, string, number][]; cost: number };
};

const SEEDS: Seed[] = [
  {
    id: 3157,
    name: '존야의 모래시계',
    bundle: {
      items: [
        [2420, '파수꾼의 갑옷', 1],
        [1052, '증폭의 고서', 2],
      ],
      cost: 1100,
    },
    description: {
      traits: ['방어력', '생존'],
      ally: [champion(412, '쓰레쉬')],
      counter: [champion(238, '제드'), champion(157, '야스오')],
    },
  },
  {
    id: 3089,
    name: '라바돈의 죽음모자',
    bundle: { items: [[1058, '쓸데없이 큰 지팡이', 1]], cost: 1200 },
    description: { traits: ['주문력'], ally: [champion(117, '룰루')], counter: [] },
  },
  {
    id: 3135,
    name: '공허의 지팡이',
    bundle: { items: [[4630, '마법 부적', 1]], cost: 900 },
    description: {
      traits: ['마법 관통력'],
      ally: [],
      counter: [champion(86, '가렌'), champion(122, '다리우스')],
    },
  },
  {
    id: 3116,
    name: '라일라이의 수정홀',
    description: { traits: ['둔화'], ally: [], counter: [champion(16, '소라카')] },
  },
  {
    id: 4645,
    name: '그림자불꽃',
    description: { traits: ['주문력', '마법 관통력'], ally: [], counter: [] },
  },
];

async function recommendations(): Promise<RecommendedItem[]> {
  const icons = await itemIcons;
  return SEEDS.map(({ bundle, ...seed }) => ({
    ...seed,
    imageUrl: icons.get(seed.id) ?? '',
    purchaseBundle: bundle
      ? {
          items: bundle.items.map(([itemId, name, quantity]) => ({
            itemId,
            name,
            quantity,
            imageUrl: icons.get(itemId) ?? '',
          })),
          additionalCost: bundle.cost,
          purchaseOrder: bundle.items.map(([itemId]) => itemId),
        }
      : null,
  }));
}

/** 상대 조합 대응 아이템. 하나는 지금 살 수 있고, 하나는 골드가 모자란 경우다. */
async function situationalItems(): Promise<SituationalItem[]> {
  const icons = await itemIcons;
  const all: SituationalItem[] = [
    {
      id: 3165,
      name: '모렐로노미콘',
      imageUrl: icons.get(3165) ?? '',
      description: {
        traits: ['치유 감소 부여'],
        ally: [],
        counter: [champion(16, '소라카'), champion(19, '워윅')],
      },
      additionalCost: 800,
      affordable: true,
    },
    {
      id: 3102,
      name: '밴시의 장막',
      imageUrl: icons.get(3102) ?? '',
      description: { traits: ['주문 보호막'], ally: [], counter: [champion(238, '제드')] },
      additionalCost: 1500,
      affordable: false,
    },
  ];
  return all.slice(0, situCount);
}

const log =
  (name: string) =>
  (...args: unknown[]) =>
    console.debug(`[mock] ${name}`, ...args);
const unsubscribe = () => {};

const OVERLAY_WIDTH = 260;
const OVERLAY_HEIGHT = 242;
const COLLAPSED_SIZE = 40;
const PREVIEW_MARGIN = 24;

let expandedHeight = OVERLAY_HEIGHT;
let collapsed = false;

/** 실제 앱에서는 메인 프로세스가 창 크기를 바꾼다. 여기서는 틀 크기를 바꿔 흉내 낸다. */
function resizePreview() {
  const root = document.getElementById('root');
  if (!root) return;

  const [width, height] = collapsed
    ? [COLLAPSED_SIZE, COLLAPSED_SIZE]
    : [OVERLAY_WIDTH, expandedHeight];
  root.style.cssText = `width:${width}px;height:${height}px;margin:${PREVIEW_MARGIN}px`;
}

/**
 * 브라우저 미리보기 전용 꾸밈: 어두운 바탕을 깔고, 실제 오버레이 크기로 틀을 잡는다.
 * Electron 창은 투명하고 창 크기가 곧 오버레이 크기라서, 여기 손대면 배경이 생기고 창을 끌 수 없게 된다.
 * 반드시 아래 `if (!window.lcu)` 안에서만 부른다.
 */
function setUpBrowserPreview() {
  document.documentElement.style.background = '#1c1e22';
  window.addEventListener('DOMContentLoaded', resizePreview);
}

// Electron 안에서는 진짜 preload 가 이미 있으므로 아무것도 건드리지 않는다. 미리보기 꾸밈도 마찬가지다.
if (!window.lcu) {
  setUpBrowserPreview();

  window.lcu = {
    currentSummoner: async () => null,
    getRankInfo: async () => null,
    getProfileBackground: async () => null,
    launchClient: async () => false,
    getDfScores: async () => null,
    getState: async () => ({
      status: 'connected',
      phase: state === 'idle' ? 'None' : 'InProgress',
      recommendations: state === 'ready' ? await recommendations() : null,
      situationalItems: state === 'ready' ? await situationalItems() : [],
      purchasedCount: bought,
      availableGold: MOCK_GOLD,
    }),
    getMatchHistoryInfo: async () => null,
    getMatchDetail: async () => null,
    onStatusChange: () => unsubscribe,
    onSummonerChange: () => unsubscribe,
    onProfileBackgroundChange: () => unsubscribe,
    onPhaseChange: () => unsubscribe,
    onItemsRecommendationChange: () => unsubscribe,
    getEndedGame: async () => null,
  };
  window.windowControls = {
    setCollapsed: (next) => {
      collapsed = next;
      resizePreview();
    },
    setOverlayHeight: (height) => {
      expandedHeight = height;
      resizePreview();
    },
    minimize: log('windowControls.minimize'),
    close: log('windowControls.close'),
  };
  window.analytics = {
    track: log('analytics.track'),
    overlayClick: log('analytics.overlayClick'),
  };
  window.feedback = { submit: async () => ({}) };
}
