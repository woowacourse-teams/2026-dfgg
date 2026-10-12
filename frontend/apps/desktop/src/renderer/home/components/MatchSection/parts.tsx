import type { MatchStats } from '../../../../shared/types';
import { itemIconUrl } from '../../lib/ddragon';
import { formatRatio, kdaGrade, kdaValue } from '../../lib/kda';

/** 아이템 7칸. 안 산 칸은 0 이라 빈 네모로 자리만 잡아 줄이 흔들리지 않게 한다. */
export function ItemRow({ items, version }: { items: number[]; version: string | null }) {
  return (
    <span className='items'>
      {items.map((itemId, index) =>
        itemId && version ? (
          <img key={index} className='item-slot' src={itemIconUrl(version, itemId)} alt='' />
        ) : (
          <span key={index} className='item-slot item-slot-empty' />
        ),
      )}
    </span>
  );
}

export function KdaLine({ stats }: { stats: MatchStats }) {
  return (
    <span className='kda-line'>
      {stats.kills} <i>/</i> <b>{stats.deaths}</b> <i>/</i> {stats.assists}
    </span>
  );
}

/** '평점 4.00' 칩. 평점에 따라 색이 달라진다. */
export function RatioChip({ stats }: { stats: MatchStats }) {
  const ratio = kdaValue(stats);

  return (
    <span className={`chip chip-ratio ${kdaGrade(ratio)}`}>
      평점 <b>{formatRatio(ratio)}</b>
    </span>
  );
}

/**
 * 빗금 막대. 세로 줄무늬를 통째로 기울여서 그리므로 양 끝도 빗금과 같은 각도로 잘린다.
 * from 은 채워지기 시작하는 쪽이다.
 */
export function Hatch({ percent, from }: { percent: number; from: 'left' | 'right' }) {
  return (
    <span className={`hatch hatch-from-${from}`}>
      <span className='hatch-track'>
        <span className='hatch-fill' style={{ '--fill': `${percent}%` } as React.CSSProperties} />
      </span>
    </span>
  );
}
