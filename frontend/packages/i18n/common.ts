/**
 * 웹·데스크톱이 함께 쓰는 문구. 포지션·빌드 방향처럼 백엔드 enum을 화면 라벨로
 * 바꾸는 표가 대부분이다.
 */
import type { Position } from '../shared/types';
import type { Lang } from './lang';

export const POSITION_LABEL: Record<Lang, Record<Position, string>> = {
  ko: {
    TOP: '탑',
    JUNGLE: '정글',
    MID: '미드',
    BOTTOM: '원딜',
    SUPPORT: '서폿',
  },
  en: {
    TOP: 'Top',
    JUNGLE: 'Jungle',
    MID: 'Mid',
    BOTTOM: 'Bot',
    SUPPORT: 'Support',
  },
};

export const CHAMPION_TAG_LABEL: Record<Lang, Record<string, string>> = {
  ko: {
    TANK: '탱커',
    FIGHTER: '전사',
    MAGE: '마법사',
    ASSASSIN: '암살자',
    MARKSMAN: '원거리 딜러',
    SUPPORT: '서포터',
  },
  en: {
    TANK: 'Tank',
    FIGHTER: 'Fighter',
    MAGE: 'Mage',
    ASSASSIN: 'Assassin',
    MARKSMAN: 'Marksman',
    SUPPORT: 'Support',
  },
};

/** championTag마다 값이 겹치지 않아 태그 구분 없이 하나로 모아도 된다. */
export const DIRECTION_LABEL: Record<Lang, Record<string, string>> = {
  ko: {
    PHYSICAL_DAMAGE: '물리 피해 대응',
    MAGIC_DAMAGE: '마법 피해 대응',
    MIXED_DAMAGE: '물리·마법 복합 피해 대응',
    ANTI_TANK: '탱커 대응',
    BURST_SURVIVAL: '순간 피해 생존',
    SUSTAINED_COMBAT: '지속 전투',
    BURST_DAMAGE: '순간 피해',
    SUSTAINED_DAMAGE: '지속 피해',
    SURVIVAL_RESPONSE: '생존 대응',
    BURST_ASSASSINATION: '순간 암살',
    DEFENSE_NEUTRALIZATION: '방어 무력화',
    ENGAGE_SURVIVAL: '진입 후 생존',
    CRITICAL_STRIKE_DAMAGE: '치명타 피해',
    ANTI_TANK_SUSTAINED_DAMAGE: '대탱커 지속 피해',
    SURVIVAL_KITING: '생존 및 카이팅',
    ENGAGE_INITIATION: '전투 개시',
    ALLY_PROTECTION: '아군 보호',
    HEALING_ENHANCEMENT: '회복 및 강화',
  },
  en: {
    PHYSICAL_DAMAGE: 'Anti physical damage',
    MAGIC_DAMAGE: 'Anti magic damage',
    MIXED_DAMAGE: 'Anti mixed damage',
    ANTI_TANK: 'Anti tank',
    BURST_SURVIVAL: 'Burst survival',
    SUSTAINED_COMBAT: 'Sustained combat',
    BURST_DAMAGE: 'Burst damage',
    SUSTAINED_DAMAGE: 'Sustained damage',
    SURVIVAL_RESPONSE: 'Survivability',
    BURST_ASSASSINATION: 'Burst assassination',
    DEFENSE_NEUTRALIZATION: 'Defense shred',
    ENGAGE_SURVIVAL: 'Post-engage survival',
    CRITICAL_STRIKE_DAMAGE: 'Critical strike damage',
    ANTI_TANK_SUSTAINED_DAMAGE: 'Anti-tank sustained damage',
    SURVIVAL_KITING: 'Survival and kiting',
    ENGAGE_INITIATION: 'Engage initiation',
    ALLY_PROTECTION: 'Ally protection',
    HEALING_ENHANCEMENT: 'Healing and buffs',
  },
};

/** 모든 화면 하단에 같은 내용이 들어가는 Riot 고지 문구. */
export const FOOTER_TEXT = {
  ko: {
    privacy: '개인정보처리방침',
    disclaimer:
      'DFGG는 Riot Games와 제휴하거나 승인받은 서비스가 아닙니다. League of Legends와 Riot Games는 Riot Games, Inc.의 상표입니다.',
  },
  en: {
    privacy: 'Privacy policy',
    disclaimer:
      'DFGG is not affiliated with or endorsed by Riot Games. League of Legends and Riot Games are trademarks of Riot Games, Inc.',
  },
};
