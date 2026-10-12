import { formatNumber, formatPercent } from '../format';
import type { FunnelStep } from '../types';

/**
 * 단계별 순 방문자. 앞 단계 대비 비율을 같이 보여준다.
 * 스토어 획득은 웹·앱 분석 방문자와 다른 출처라 같은 사람이라는 보장이 없다 — 비율 계산에서 뺀다.
 */
export default function Funnel({ steps }: { steps: FunnelStep[] }) {
  const max = Math.max(...steps.map((step) => step.value ?? 0), 1);

  return (
    <ol className='space-y-3'>
      {steps.map((step, index) => {
        const external = step.unit === 'acquisitions';
        // 비율은 같은 단위(순 방문자)끼리만 낸다. 스토어 단계는 건너뛰고 그 앞 단계와 비교한다.
        const prev = steps
          .slice(0, index)
          .reverse()
          .find((s) => s.value !== null && s.unit === step.unit);
        const rate =
          !external && step.value !== null && prev?.value ? step.value / prev.value : null;

        return (
          <li key={step.key}>
            <div className='mb-1 flex items-baseline justify-between gap-3 text-sm'>
              <span className='text-ink' title={step.events.join(', ') || undefined}>
                <span className='tabular mr-2 text-ink-3'>{index + 1}</span>
                {step.label}
                {external && <span className='ml-1.5 text-xs text-ink-3'>(스토어 · 건수)</span>}
              </span>
              <span className='tabular shrink-0'>
                <span className='font-semibold text-ink'>
                  {step.value === null ? '—' : formatNumber(step.value)}
                </span>
                {rate !== null && (
                  <span className='ml-2 text-xs text-ink-2'>→ {formatPercent(rate)}</span>
                )}
              </span>
            </div>
            <div className='h-2.5 rounded-full bg-surface-2'>
              {step.value !== null && (
                <div
                  className={`h-full rounded-full ${external ? 'bg-series-3' : 'bg-series-1'}`}
                  style={{
                    width: `${Math.max((step.value / max) * 100, step.value > 0 ? 1 : 0)}%`,
                  }}
                />
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
