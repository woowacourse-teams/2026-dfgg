import { useEffect, useMemo, useRef, useState } from 'react';

import { formatNumber, shortDate } from '../format';

export interface TrendSeries {
  key: string;
  label: string;
  color: string;
  // 없는 날(아직 집계 전 등)은 null — 선을 끊는다.
  values: (number | null)[];
}

interface TrendChartProps {
  dates: string[];
  series: TrendSeries[];
}

const HEIGHT = 260;
const MARGIN = { top: 12, right: 16, bottom: 28, left: 44 };

function niceMax(value: number) {
  if (value <= 0) return 4;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const step = [1, 2, 2.5, 5, 10].find((s) => s * magnitude * 4 >= value) ?? 10;
  return step * magnitude * 4;
}

function useWidth() {
  const ref = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(640);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return { ref, width };
}

export default function TrendChart({ dates, series }: TrendChartProps) {
  const { ref, width } = useWidth();
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const [hover, setHover] = useState<number | null>(null);
  const [showTable, setShowTable] = useState(false);

  const visible = series.filter((s) => !hidden.has(s.key));
  const innerWidth = Math.max(width - MARGIN.left - MARGIN.right, 10);
  const innerHeight = HEIGHT - MARGIN.top - MARGIN.bottom;

  const yMax = useMemo(
    () => niceMax(Math.max(0, ...visible.flatMap((s) => s.values.map((v) => v ?? 0)))),
    [visible],
  );
  const x = (index: number) =>
    MARGIN.left + (dates.length <= 1 ? innerWidth / 2 : (index / (dates.length - 1)) * innerWidth);
  const y = (value: number) => MARGIN.top + innerHeight - (value / yMax) * innerHeight;

  const ticks = [0, 0.25, 0.5, 0.75, 1].map((r) => r * yMax);
  const labelEvery = Math.max(
    1,
    Math.ceil(dates.length / Math.max(2, Math.floor(innerWidth / 56))),
  );

  const path = (values: (number | null)[]) => {
    let d = '';
    let pen = false;
    values.forEach((value, index) => {
      if (value === null) {
        pen = false;
        return;
      }
      d += `${pen ? 'L' : 'M'}${x(index).toFixed(1)},${y(value).toFixed(1)}`;
      pen = true;
    });
    return d;
  };

  const onMove = (event: React.MouseEvent<SVGRectElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const ratio = (event.clientX - rect.left) / rect.width;
    setHover(Math.round(ratio * (dates.length - 1)));
  };

  const toggle = (key: string) =>
    setHidden((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  const tooltipLeft = hover !== null ? x(hover) : 0;

  return (
    <div>
      <div className='mb-3 flex flex-wrap items-center justify-between gap-2'>
        <div className='flex flex-wrap gap-2' role='group' aria-label='계열 표시'>
          {series.map((s) => {
            const on = !hidden.has(s.key);
            return (
              <button
                key={s.key}
                type='button'
                aria-pressed={on}
                onClick={() => toggle(s.key)}
                className={`flex items-center gap-2 rounded-full border px-3 py-1 text-xs transition ${
                  on ? 'border-line text-ink' : 'border-line/50 text-ink-3 line-through'
                }`}
              >
                <span
                  className='inline-block h-0.5 w-4 rounded'
                  style={{ backgroundColor: on ? s.color : 'currentColor' }}
                />
                {s.label}
              </button>
            );
          })}
        </div>
        <button
          type='button'
          onClick={() => setShowTable((v) => !v)}
          className='text-xs text-ink-2 underline-offset-2 hover:underline'
        >
          {showTable ? '차트로 보기' : '표로 보기'}
        </button>
      </div>

      {showTable ? (
        <div className='max-h-72 overflow-auto'>
          <table className='tabular w-full text-sm'>
            <thead className='sticky top-0 bg-surface text-xs text-ink-3'>
              <tr>
                <th className='py-1 text-left font-medium'>날짜</th>
                {series.map((s) => (
                  <th key={s.key} className='py-1 text-right font-medium'>
                    {s.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {dates.map((date, index) => (
                <tr key={date} className='border-t border-line/60'>
                  <td className='py-1 text-ink-2'>{date}</td>
                  {series.map((s) => (
                    <td key={s.key} className='py-1 text-right'>
                      {s.values[index] === null ? '—' : formatNumber(s.values[index]!)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div ref={ref} className='relative'>
          <svg width={width} height={HEIGHT} role='img' aria-label='일별 추이 차트'>
            {ticks.map((tick) => (
              <g key={tick}>
                <line
                  x1={MARGIN.left}
                  x2={MARGIN.left + innerWidth}
                  y1={y(tick)}
                  y2={y(tick)}
                  stroke='var(--color-line)'
                  strokeWidth={1}
                />
                <text
                  x={MARGIN.left - 8}
                  y={y(tick)}
                  textAnchor='end'
                  dominantBaseline='middle'
                  className='tabular fill-ink-3 text-[11px]'
                >
                  {formatNumber(tick)}
                </text>
              </g>
            ))}
            {dates.map((date, index) =>
              index % labelEvery === 0 || index === dates.length - 1 ? (
                <text
                  key={date}
                  x={x(index)}
                  y={HEIGHT - 8}
                  textAnchor='middle'
                  className='tabular fill-ink-3 text-[11px]'
                >
                  {shortDate(date)}
                </text>
              ) : null,
            )}

            {hover !== null && (
              <line
                x1={x(hover)}
                x2={x(hover)}
                y1={MARGIN.top}
                y2={MARGIN.top + innerHeight}
                stroke='var(--color-ink-3)'
                strokeWidth={1}
              />
            )}

            {visible.map((s) => (
              <path
                key={s.key}
                d={path(s.values)}
                fill='none'
                stroke={s.color}
                strokeWidth={2}
                strokeLinejoin='round'
                strokeLinecap='round'
              />
            ))}

            {hover !== null &&
              visible.map((s) =>
                s.values[hover] === null ? null : (
                  <circle
                    key={s.key}
                    cx={x(hover)}
                    cy={y(s.values[hover]!)}
                    r={4}
                    fill={s.color}
                    stroke='var(--color-surface)'
                    strokeWidth={2}
                  />
                ),
              )}

            <rect
              x={MARGIN.left}
              y={MARGIN.top}
              width={innerWidth}
              height={innerHeight}
              fill='transparent'
              onMouseMove={onMove}
              onMouseLeave={() => setHover(null)}
            />
          </svg>

          {hover !== null && dates[hover] && (
            <div
              className='pointer-events-none absolute top-2 z-10 min-w-36 rounded-md border border-line bg-surface-2 px-3 py-2 text-xs shadow-lg'
              style={
                tooltipLeft > width / 2
                  ? { right: width - tooltipLeft + 12 }
                  : { left: tooltipLeft + 12 }
              }
            >
              <p className='mb-1 font-semibold text-ink'>{dates[hover]}</p>
              {visible.map((s) => (
                <p key={s.key} className='flex items-center justify-between gap-4 text-ink-2'>
                  <span className='flex items-center gap-1.5'>
                    <span
                      className='inline-block h-2 w-2 rounded-full'
                      style={{ backgroundColor: s.color }}
                    />
                    {s.label}
                  </span>
                  <span className='tabular text-ink'>
                    {s.values[hover] === null ? '—' : formatNumber(s.values[hover]!)}
                  </span>
                </p>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
