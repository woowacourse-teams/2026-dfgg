import { strings } from '../../i18n/i18n';

export interface DemoItem {
  id: number;
  name: string;
  traits: string[];
  ally: string[];
  counter: string[];
}

const text = strings();

const item = (id: number, ally: string[] = [], counter: string[] = []): DemoItem => ({
  id,
  name: text.items[id].name,
  traits: text.items[id].traits,
  ally,
  counter,
});

const ITEMS: Record<number, DemoItem> = Object.fromEntries(
  [
    item(2510, ['Lulu'], ['TahmKench']),
    item(3158, [], ['Seraphine']),
    item(6675, ['Sivir'], ['Yunara']),
    item(3065, ['Lulu'], ['Sylas', 'Ahri']),
    item(3047, [], ['Yunara']),
    item(3111, [], ['Ahri', 'Seraphine']),
    item(3075, [], ['Sylas', 'Yunara']),
    item(3742, ['Jax'], ['TahmKench']),
    item(2502, [], ['Sylas']),
    item(3143, [], ['Yunara']),
    item(4401, ['Veigar'], ['Ahri']),
    item(6665, ['Jax'], ['TahmKench', 'Seraphine']),
    item(2504, [], ['Veigar']),
    item(4629, ['Veigar'], []),
  ].map((entry) => [entry.id, entry]),
);

const RECOMMENDATION_STEPS = [
  [2510, 3158, 6675, 3065, 3047],
  [3111, 6675, 3047, 3158, 3065],
  [6675, 3065, 3075, 3742, 2502],
  [3065, 3075, 3742, 2502, 3143],
  [3075, 3742, 4401, 2502, 6665],
  [6665, 2504, 4629, 2502, 3742],
];

export const INVENTORY_ORDER = RECOMMENDATION_STEPS.map(([first]) => ITEMS[first]);

export const recommendationsAfter = (purchased: number) =>
  (RECOMMENDATION_STEPS[purchased] ?? []).map((id) => ITEMS[id]);

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
