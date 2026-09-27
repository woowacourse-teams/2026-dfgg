export interface DemoItem {
  id: number;
  name: string;
  traits: string[];
  ally: string[];
  counter: string[];
}

const item = (
  id: number,
  name: string,
  traits: string[],
  ally: string[] = [],
  counter: string[] = [],
): DemoItem => ({ id, name, traits, ally, counter });

const ITEMS: Record<number, DemoItem> = Object.fromEntries(
  [
    item(2510, '황혼과 새벽', ['주문검', '공격 속도'], ['Lulu'], ['TahmKench']),
    item(3158, '명석함의 아이오니아 장화', ['스킬 가속'], [], ['Seraphine']),
    item(6675, '나보리 명멸검', ['치명타', '스킬 쿨타임 감소'], ['Sivir'], ['Yunara']),
    item(3065, '정령의 형상', ['마법 저항력', '회복 및 보호막 강화'], ['Lulu'], ['Sylas', 'Ahri']),
    item(3047, '판금 장화', ['방어력', '기본 공격 피해 감소'], [], ['Yunara']),
    item(3111, '헤르메스의 발걸음', ['마법 저항력', '강인함'], [], ['Ahri', 'Seraphine']),
    item(3075, '가시 갑옷', ['치유 감소', '방어력'], [], ['Sylas', 'Yunara']),
    item(3742, '망자의 갑옷', ['방어력', '이동 속도'], ['Jax'], ['TahmKench']),
    item(2502, '끝없는 절망', ['방어력', '스킬 가속'], [], ['Sylas']),
    item(3143, '란두인의 예언', ['방어력', '치명타 피해 감소'], [], ['Yunara']),
    item(4401, '대자연의 힘', ['마법 저항력', '이동 속도'], ['Veigar'], ['Ahri']),
    item(6665, '해신 작쇼', ['방어력', '마법 저항력'], ['Jax'], ['TahmKench', 'Seraphine']),
    item(2504, '케이닉 루컨', ['마법 저항력', '보호막'], [], ['Veigar']),
    item(4629, '우주의 추진력', ['주문력', '이동 속도'], ['Veigar'], []),
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

export const STAGES = [
  {
    start: 0,
    label: '게임 시작',
    title: '게임 안에서 바로 보이게!',
    body: '게임이 시작되면 1~3순위 코어와 2개의 대안 템이 화면에 뜹니다.',
  },
  {
    start: 0.2,
    label: '아이템 구매',
    title: '템을 사면 알아서 다음 코어 추천!',
    body: '하나 살 때마다 추천이 새로 갱신됩니다.',
  },
  {
    start: 0.4,
    label: '추천 이유',
    title: '왜 이 템을 추천할까? 설명까지!',
    body: '누구를 상대하기 좋은지,\n어떤 아군과 잘 맞는지까지 확인할 수 있어요.',
  },
  {
    start: 0.6,
    label: '풀템 완성',
    title: '이제 시작해볼까요?!',
    body: '추천을 따라 하나씩 사다 보면 여섯 칸이 채워집니다.',
  },
] as const;

export const stageAt = (progress: number) =>
  STAGES.reduce((current, stage, index) => (progress >= stage.start ? index : current), 0);

export const CHAMPION_NAMES: Record<string, string> = {
  Jax: '잭스',
  Veigar: '베이가',
  Sivir: '시비르',
  Lulu: '룰루',
  Sylas: '사일러스',
  Yunara: '유나라',
  Ahri: '아리',
  TahmKench: '탐 켄치',
  Seraphine: '세라핀',
};
