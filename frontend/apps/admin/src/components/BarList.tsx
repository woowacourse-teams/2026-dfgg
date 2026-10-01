import { formatNumber } from '../format';
import type { Point } from '../types';
import { Unavailable } from './Panel';

interface BarListProps {
  items: Point[];
  label?: (x: string) => string;
  empty?: string;
}

// 순위 목록. 막대는 1위 대비 길이이고, 숫자는 항상 글자로 같이 보인다.
export default function BarList({ items, label = (x) => x, empty = '데이터 없음' }: BarListProps) {
  if (items.length === 0) return <Unavailable>{empty}</Unavailable>;
  const max = Math.max(...items.map((item) => item.y), 1);

  return (
    <ul className='space-y-1.5'>
      {items.map((item) => (
        <li
          key={item.x}
          className='relative flex items-center justify-between gap-3 px-2 py-1 text-sm'
        >
          <span
            className='absolute inset-y-0 left-0 rounded-r bg-cobalt-deep'
            style={{ width: `${(item.y / max) * 100}%` }}
            aria-hidden
          />
          <span className='relative truncate text-ink' title={item.x}>
            {label(item.x) || '(직접 방문)'}
          </span>
          <span className='tabular relative shrink-0 text-ink-2'>{formatNumber(item.y)}</span>
        </li>
      ))}
    </ul>
  );
}
