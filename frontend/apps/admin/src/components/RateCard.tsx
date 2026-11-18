import { formatNumber, formatPercent } from '../format';
import type { Rate } from '../types';

// 추천 요청 성공률. 성공/실패 두 칸 막대 + 숫자. 색만으로 구분하지 않도록 글자를 같이 둔다.
export default function RateCard({ label, rate }: { label: string; rate: Rate }) {
  const total = rate.success + rate.fail;
  const ratio = total > 0 ? rate.success / total : null;

  return (
    <div>
      <div className='mb-1.5 flex items-baseline justify-between'>
        <span className='text-sm text-ink'>{label}</span>
        <span className='tabular font-display text-2xl font-bold text-ink'>
          {ratio === null ? '—' : formatPercent(ratio)}
        </span>
      </div>
      <div className='flex h-2.5 gap-0.5 overflow-hidden rounded-full bg-surface-2'>
        {total > 0 && (
          <>
            <div className='bg-win' style={{ width: `${(rate.success / total) * 100}%` }} />
            <div className='bg-loss' style={{ width: `${(rate.fail / total) * 100}%` }} />
          </>
        )}
      </div>
      <p className='tabular mt-1.5 text-xs text-ink-3'>
        성공 {formatNumber(rate.success)} · 실패·오류 {formatNumber(rate.fail)}
      </p>
    </div>
  );
}
