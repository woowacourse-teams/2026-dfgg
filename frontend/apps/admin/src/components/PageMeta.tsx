import { formatDateTime } from '../format';

interface PageMetaProps {
  generatedAt?: number;
  mock?: boolean;
  loading: boolean;
}

// 페이지 맨 위 한 줄: 기준 시각, mock 여부, 불러오는 중 표시
export default function PageMeta({ generatedAt, mock, loading }: PageMetaProps) {
  return (
    <div className='flex min-h-5 items-center gap-2 text-xs text-ink-3'>
      {generatedAt && <span>{formatDateTime(generatedAt)} 기준</span>}
      {mock && (
        <span className='rounded bg-mine/20 px-1.5 py-0.5 font-semibold text-mine'>
          MOCK 데이터
        </span>
      )}
      {loading && <span className='text-ink-2'>불러오는 중…</span>}
    </div>
  );
}
