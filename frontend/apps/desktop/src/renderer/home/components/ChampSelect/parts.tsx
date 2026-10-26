import { useState } from 'react';

import type { RuneEntry, TodayPick } from '../../../../shared/types';
import { t } from '../../../i18n';
import { championSplashUrl } from '../../lib/ddragon';

const CDRAGON_PERK_STYLES =
  'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/perk-images/styles';

// 오늘의 픽은 룬 트리에 그림 주소를 주지 않는다. 트리는 다섯 개뿐이라 이름표를 박아 둔다.
const TREE_ICONS: Record<number, string> = {
  8000: '7201_precision.png',
  8100: '7200_domination.png',
  8200: '7202_sorcery.png',
  8300: '7203_whimsy.png',
  8400: '7204_resolve.png',
};

export const runeTreeIconUrl = (id: number): string | undefined =>
  TREE_ICONS[id] && `${CDRAGON_PERK_STYLES}/${TREE_ICONS[id]}`;

/** 룬 그림. 새 패치 직후처럼 카탈로그에 없으면 그림 없이 자리만 잡는다. */
export function RuneIcon({
  rune,
  size,
  className = '',
}: {
  rune: RuneEntry;
  size: number;
  className?: string;
}) {
  const label = rune.name ?? '';
  return rune.imageUrl ? (
    <img
      className={`cs-icon ${className}`}
      src={rune.imageUrl}
      alt={label}
      title={label}
      width={size}
      height={size}
    />
  ) : (
    <span className={`cs-icon cs-icon-empty ${className}`} style={{ width: size, height: size }} />
  );
}

/** rank 1 은 '추천', 그 아래는 '대안 n'. 문구는 프론트가 정하기로 했다. */
export const pageLabel = (rank: number): string =>
  rank === 1 ? t.champSelect.recommended : t.champSelect.alternative(rank - 1);

/** '2026-10-16' → '10.16' */
export const shortDate = (date: string): string => date.slice(5).replace('-', '.');

/**
 * 선수 사진. Leaguepedia 사진은 없거나(null) 못 열릴 수 있어서, 그럴 땐 그 판 챔피언 그림으로 대신한다.
 * 사진이 비면 '오늘의 픽' 자리가 통째로 허전해진다.
 */
export function ProPhoto({ pick, className = '' }: { pick: TodayPick; className?: string }) {
  const [broken, setBroken] = useState(false);
  const photo = broken ? null : pick.player.imageUrl;

  return photo ? (
    <img className={`cs-photo ${className}`} src={photo} alt='' onError={() => setBroken(true)} />
  ) : (
    <img
      className={`cs-photo cs-photo-fallback ${className}`}
      src={championSplashUrl(pick.champion.id)}
      alt=''
    />
  );
}

/** 주문 둘 + 아이템. 프로가 실제로 들고 끝낸 것. */
export function Build({ pick, size }: { pick: TodayPick; size: number }) {
  return (
    <span className='cs-build'>
      {pick.spells.map((spell) => (
        <img
          key={spell.id}
          className='cs-icon cs-spell'
          src={spell.imageUrl}
          alt={spell.name}
          title={spell.name}
          width={size}
          height={size}
        />
      ))}
      {/* 주문이 비어 오는 대회 기록이 있어 그땐 사이 틈도 뺀다 */}
      {pick.spells.length > 0 && <i className='cs-build-gap' />}
      {pick.items.map((item) => (
        <img
          key={item.id}
          className='cs-icon cs-item'
          src={item.imageUrl}
          alt={item.name}
          title={item.name}
          width={size}
          height={size}
        />
      ))}
    </span>
  );
}
