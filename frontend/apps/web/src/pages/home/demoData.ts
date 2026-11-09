import { strings } from '../../i18n/i18n';

export interface DemoPart {
  id: number;
  name: string;
  quantity: number;
}

/** 코어 하나를 향해 지금 살 하위 아이템. 데스크톱 오버레이의 purchaseBundle 과 같은 모양이다. */
export interface DemoBundle {
  parts: DemoPart[];
  cost: number;
}

export interface DemoItem {
  id: number;
  name: string;
  traits: string[];
  ally: string[];
  counter: string[];
  bundle: DemoBundle | null;
}

/** 상대 조합 때문에 고려할 아이템. 지금 골드로 못 사면 흐리게 보인다. */
export interface DemoSituational extends DemoItem {
  cost: number;
  isAffordable: boolean;
}

const text = strings();

/** 데모 속 보유 골드. 상황템의 모자란 골드를 계산할 때 쓴다. */
export const DEMO_GOLD = 1200;

const part = (id: number, quantity = 1): DemoPart => ({ id, name: text.items[id].name, quantity });

const bundle = (cost: number, ...parts: DemoPart[]): DemoBundle => ({ parts, cost });

const item = (
  id: number,
  ally: string[] = [],
  counter: string[] = [],
  itemBundle: DemoBundle | null = null,
): DemoItem => ({
  id,
  name: text.items[id].name,
  traits: text.items[id].traits,
  ally,
  counter,
  bundle: itemBundle,
});

const ITEMS: Record<number, DemoItem> = Object.fromEntries(
  [
    item(2510, ['Lulu'], ['TahmKench'], bundle(700, part(3057))),
    item(3158, [], ['Seraphine'], bundle(300, part(1001))),
    item(6675, ['Sivir'], ['Yunara']),
    item(3065, ['Lulu'], ['Sylas', 'Ahri'], bundle(1150, part(3067), part(3211))),
    item(3047, [], ['Yunara'], bundle(600, part(1001), part(1029))),
    item(3111, [], ['Ahri', 'Seraphine'], bundle(750, part(1001), part(1033))),
    item(3075, [], ['Sylas', 'Yunara']),
    item(3742, ['Jax'], ['TahmKench'], bundle(1250, part(1031), part(1011))),
    item(2502, [], ['Sylas']),
    item(3143, [], ['Yunara'], bundle(1900, part(3082), part(1011))),
    item(4401, ['Veigar'], ['Ahri']),
    item(6665, ['Jax'], ['TahmKench', 'Seraphine']),
    item(2504, [], ['Veigar']),
    item(4629, ['Veigar'], []),
  ].map((entry) => [entry.id, entry]),
);

const situational = (id: number, cost: number): DemoSituational => ({
  ...ITEMS[id],
  cost,
  isAffordable: cost <= DEMO_GOLD,
});

interface DemoStep {
  cores: number[];
  situational: DemoSituational[];
}

/**
 * 아이템을 하나 살 때마다 바뀌는 추천. 코어와 상황템을 합쳐 다섯 줄이다(데스크톱 오버레이와 같은 규칙).
 * 각 단계의 첫 코어가 그 단계에서 사는 아이템이다.
 */
const RECOMMENDATION_STEPS: DemoStep[] = [
  { cores: [2510, 3158, 6675, 3065, 3047], situational: [] },
  {
    cores: [3111, 6675, 3047],
    situational: [situational(3075, 1000), situational(3143, 1900)],
  },
  { cores: [6675, 3065, 3742, 2502], situational: [situational(3075, 1000)] },
  { cores: [3065, 3075, 3742, 2502, 3143], situational: [] },
  { cores: [3075, 3742, 4401, 2502, 6665], situational: [] },
  { cores: [6665, 2504, 4629, 2502, 3742], situational: [] },
];

export const INVENTORY_ORDER = RECOMMENDATION_STEPS.map(({ cores: [first] }) => ITEMS[first]);

const EMPTY_STEP = { cores: [], situational: [] };

export const recommendationsAfter = (purchased: number) => {
  const step = RECOMMENDATION_STEPS[purchased] ?? EMPTY_STEP;
  return { cores: step.cores.map((id) => ITEMS[id]), situational: step.situational };
};

const STAGE_KEYS = ['game-start', 'purchase', 'reason', 'full-build'] as const;
const STAGE_STARTS = [0, 0.2, 0.4, 0.6];

export const STAGES = text.demo.stages.map((stage, index) => ({
  ...stage,
  key: STAGE_KEYS[index],
  start: STAGE_STARTS[index],
}));

export const stageAt = (progress: number) =>
  STAGES.reduce((current, stage, index) => (progress >= stage.start ? index : current), 0);

export const CHAMPION_NAMES = text.champions;
