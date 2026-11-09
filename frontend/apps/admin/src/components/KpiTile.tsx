import { change, formatNumber, formatPercent } from '../format';

interface KpiTileProps {
  label: string;
  value: number | null;
  prev?: number | null;
  hint?: string;
  format?: (value: number) => string;
}

export default function KpiTile({ label, value, prev, hint, format = formatNumber }: KpiTileProps) {
  const delta = value !== null && prev != null ? change(value, prev) : null;
  const up = delta !== null && delta >= 0;

  return (
    <div className='rounded-lg border border-line bg-surface px-5 py-4'>
      <p className='text-xs font-medium text-ink-2'>{label}</p>
      <p className='tabular mt-2 font-display text-3xl font-bold text-ink'>
        {value === null ? '—' : format(value)}
      </p>
      {prev !== undefined && (
        <p className='mt-1 min-h-4 text-xs whitespace-nowrap'>
          {delta !== null ? (
            <>
              <span className={`tabular font-semibold ${up ? 'text-win' : 'text-loss'}`}>
                {/* 색만으로 방향을 알리지 않도록 화살표를 같이 붙인다 */}
                {up ? '▲' : '▼'} {formatPercent(Math.abs(delta))}
              </span>
              <span className='ml-1.5 text-ink-3'>이전 기간 대비</span>
            </>
          ) : (
            <span className='text-ink-3'>
              {value === null ? '데이터 없음' : '비교 데이터 없음'}
            </span>
          )}
        </p>
      )}
      {hint && <p className='mt-0.5 text-xs text-ink-3'>{hint}</p>}
    </div>
  );
}
