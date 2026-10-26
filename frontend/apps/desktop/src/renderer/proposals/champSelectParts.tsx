import { useState } from 'react';

import { type Named, type RunePage, type TodayPick } from './data';
import { championSplash, type IconOf, type Icons } from './icons';

/** 아이콘 하나. 주소를 받기 전에는 빈 칸으로 자리만 잡는다. */
export function Icon({
  of,
  entry,
  size,
  className = '',
}: {
  of: IconOf;
  entry: Named;
  size: number;
  className?: string;
}) {
  return (
    <img
      className={`cs-icon ${className}`}
      src={of(entry.id)}
      alt={entry.name}
      title={entry.name}
      width={size}
      height={size}
    />
  );
}

/**
 * 선수 사진. Leaguepedia 사진은 없거나(null) 못 열릴 수 있어서, 그럴 땐 그 판 챔피언 그림으로 대신한다.
 * 사진이 비어 보이면 '오늘의 픽' 자리가 통째로 허전해진다.
 */
export function ProPhoto({ pick, className = '' }: { pick: TodayPick; className?: string }) {
  const [broken, setBroken] = useState(false);
  const photo = !broken ? pick.player.imageUrl : null;

  return photo ? (
    <img className={`cs-photo ${className}`} src={photo} alt='' onError={() => setBroken(true)} />
  ) : (
    <img
      className={`cs-photo cs-photo-fallback ${className}`}
      src={championSplash(pick.champion.id)}
      alt=''
    />
  );
}

/** 주문 둘 + 아이템. 프로가 실제로 들고 끝낸 것. */
export function Build({ pick, icons, size }: { pick: TodayPick; icons: Icons; size: number }) {
  return (
    <span className='cs-build'>
      {pick.spells.map((spell) => (
        <Icon key={spell.id} of={icons.spell} entry={spell} size={size} className='cs-spell' />
      ))}
      <i className='cs-build-gap' />
      {pick.items.map((item) => (
        <Icon key={item.id} of={icons.item} entry={item} size={size} className='cs-item' />
      ))}
    </span>
  );
}

/** rank 1 은 '추천', 그 아래는 '대안'. 문구는 프론트가 정하기로 했다. */
export const pageLabel = (page: RunePage): string =>
  page.rank === 1 ? '추천' : `대안 ${page.rank - 1}`;

/** Leaguepedia(CC BY-SA 3.0) 출처. 이 영역에 꼭 적어야 한다. */
export function Credit() {
  return <p className='cs-credit'>선수·경기 데이터 Leaguepedia · CC BY-SA 3.0</p>;
}

/** 밴픽 중이라는 것과 지금 내 챔피언·포지션. 화면 맨 위에 한 줄로 둔다. */
export function ContextBar({ champion, position }: { champion: Named; position: string }) {
  return (
    <header className='cs-context'>
      <span className='cs-phase'>밴픽</span>
      <b>{champion.name}</b>
      <span>{position}</span>
    </header>
  );
}
