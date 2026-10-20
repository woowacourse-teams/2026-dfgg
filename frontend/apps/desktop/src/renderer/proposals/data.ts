/**
 * 시안에 쓰는 가짜 데이터. 백엔드 문서(추천 v4, DF 스코어)의 예시 응답 모양을 따른다.
 * DF 스코어 응답은 문서에 data 가 {} 로만 적혀 있어서, 참가자별 점수와 순위가 온다고 가정했다.
 */

export type Champion = { id: number; name: string };

export type BundleItem = { itemId: number; name: string; quantity: number };

export type CoreItem = {
  id: number;
  name: string;
  traits: string[];
  ally: Champion[];
  counter: Champion[];
  purchaseBundle: {
    items: BundleItem[];
    additionalCost: number;
    purchaseOrder: number[];
  } | null;
};

export type SituationalItem = {
  id: number;
  name: string;
  traits: string[];
  counter: Champion[];
  additionalCost: number;
  affordable: boolean;
};

export const AVAILABLE_GOLD = 1200;
export const PURCHASED_CORES = 1;

export const CORES: CoreItem[] = [
  {
    id: 3142,
    name: '요우무의 유령검',
    traits: ['물리 관통력', '이동 속도'],
    ally: [{ id: 64, name: '리 신' }],
    counter: [{ id: 51, name: '케이틀린' }],
    purchaseBundle: {
      items: [
        { itemId: 3070, name: '여신의 눈물', quantity: 1 },
        { itemId: 1036, name: '롱소드', quantity: 1 },
      ],
      additionalCost: 750,
      purchaseOrder: [3070, 1036],
    },
  },
  {
    id: 3508,
    name: '정수 약탈자',
    traits: ['치명타', '마나 회복'],
    ally: [],
    counter: [{ id: 412, name: '쓰레쉬' }],
    purchaseBundle: {
      items: [{ itemId: 1037, name: '곡괭이', quantity: 1 }],
      additionalCost: 875,
      purchaseOrder: [1037],
    },
  },
  {
    id: 6672,
    name: '크라켄 학살자',
    traits: ['공격 속도'],
    ally: [],
    counter: [],
    // 골드가 모자라 지금 살 수 있는 묶음이 없는 경우
    purchaseBundle: null,
  },
];

export const SITUATIONAL: SituationalItem[] = [
  {
    id: 3123,
    name: '처형인의 대검',
    traits: ['치유 감소 부여'],
    counter: [{ id: 19, name: '워윅' }],
    additionalCost: 800,
    affordable: true,
  },
  {
    id: 3156,
    name: '맬모셔스의 아귀',
    traits: ['마법 보호막'],
    counter: [
      { id: 238, name: '제드' },
      { id: 103, name: '아리' },
    ],
    additionalCost: 1300,
    affordable: false,
  },
];

export type Player = {
  championId: number;
  name: string;
  team: 'mine' | 'theirs';
  kda: [number, number, number];
  damage: number;
  /** DF 스코어. 0~100 이라고 가정 */
  score: number;
  /** 10명 중 순위 */
  rank: number;
  isMe?: boolean;
};

// 라인 순서(탑 → 정글 → 미드 → 원딜 → 서폿)대로 우리 팀 5명, 상대 팀 5명
export const PLAYERS: Player[] = [
  {
    championId: 86,
    name: 'T1 Gumayusi',
    team: 'mine',
    kda: [4, 3, 6],
    damage: 18_420,
    score: 71.2,
    rank: 6,
  },
  {
    championId: 64,
    name: '정글차이',
    team: 'mine',
    kda: [6, 2, 11],
    damage: 15_980,
    score: 88.9,
    rank: 2,
  },
  {
    championId: 103,
    name: 'Faker',
    team: 'mine',
    kda: [9, 1, 8],
    damage: 31_250,
    score: 94.1,
    rank: 1,
  },
  {
    championId: 81,
    name: 'Hide on bush',
    team: 'mine',
    kda: [7, 2, 9],
    damage: 27_640,
    score: 86.3,
    rank: 3,
    isMe: true,
  },
  {
    championId: 89,
    name: '서폿유저',
    team: 'mine',
    kda: [1, 4, 19],
    damage: 6_210,
    score: 74.5,
    rank: 5,
  },
  {
    championId: 266,
    name: '탑은내가간다',
    team: 'theirs',
    kda: [3, 6, 4],
    damage: 16_330,
    score: 58.0,
    rank: 8,
  },
  {
    championId: 104,
    name: 'Deft',
    team: 'theirs',
    kda: [5, 5, 3],
    damage: 14_050,
    score: 63.7,
    rank: 7,
  },
  {
    championId: 238,
    name: 'ShowMaker',
    team: 'theirs',
    kda: [8, 6, 2],
    damage: 25_870,
    score: 77.4,
    rank: 4,
  },
  {
    championId: 51,
    name: '원딜장인',
    team: 'theirs',
    kda: [2, 7, 3],
    damage: 13_220,
    score: 44.8,
    rank: 10,
  },
  {
    championId: 412,
    name: '미드오픈',
    team: 'theirs',
    kda: [0, 7, 9],
    damage: 4_980,
    score: 52.1,
    rank: 9,
  },
];

export const ME = PLAYERS.find((player) => player.isMe)!;
