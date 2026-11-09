import { useMemo, useState } from 'react';

import { formatNumber } from '../format';
import type { EventCount, Platform } from '../types';

const TABS: { key: Platform | 'all'; label: string }[] = [
  { key: 'all', label: '전체' },
  { key: 'web', label: '웹' },
  { key: 'desktop', label: '데스크탑' },
];

export default function EventTable({ events }: { events: EventCount[] }) {
  const [tab, setTab] = useState<Platform | 'all'>('all');
  const [query, setQuery] = useState('');

  const rows = useMemo(
    () =>
      events
        .filter((event) => tab === 'all' || event.platform === tab)
        .filter((event) => event.name.includes(query.trim()))
        .sort((a, b) => b.count - a.count),
    [events, tab, query],
  );
  const max = Math.max(...rows.map((row) => row.count), 1);

  return (
    <div>
      <div className='mb-3 flex flex-wrap items-center gap-2'>
        <div className='flex rounded-md border border-line p-0.5' role='tablist'>
          {TABS.map(({ key, label }) => (
            <button
              key={key}
              type='button'
              role='tab'
              aria-selected={tab === key}
              onClick={() => setTab(key)}
              className={`rounded px-3 py-1 text-xs ${
                tab === key ? 'bg-cobalt-deep text-ink' : 'text-ink-2 hover:text-ink'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder='이벤트 이름 검색'
          className='min-w-40 flex-1 rounded-md border border-line bg-ground px-3 py-1.5 text-sm text-ink placeholder:text-ink-3 focus:border-cobalt focus:outline-none'
        />
      </div>

      <div className='max-h-96 overflow-auto'>
        <table className='tabular w-full text-sm'>
          <thead className='sticky top-0 bg-surface text-xs text-ink-3'>
            <tr>
              <th className='py-1.5 text-left font-medium'>이벤트</th>
              <th className='w-20 py-1.5 text-left font-medium'>구분</th>
              <th className='w-2/5 py-1.5 text-right font-medium'>횟수</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.name} className='border-t border-line/60'>
                <td className='py-1.5 pr-2 font-mono text-xs text-ink'>{row.name}</td>
                <td className='py-1.5 text-xs text-ink-2'>
                  {row.platform === 'desktop' ? '데스크탑' : '웹'}
                </td>
                <td className='py-1.5'>
                  <div className='flex items-center justify-end gap-2'>
                    <div className='h-1.5 flex-1 rounded-full bg-surface-2'>
                      <div
                        className='ml-auto h-full rounded-full bg-cobalt'
                        style={{ width: `${(row.count / max) * 100}%` }}
                      />
                    </div>
                    <span className='w-14 text-right text-ink'>{formatNumber(row.count)}</span>
                  </div>
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr>
                <td colSpan={3} className='py-6 text-center text-ink-3'>
                  해당하는 이벤트가 없습니다.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
