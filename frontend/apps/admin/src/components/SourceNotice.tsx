import type { Source } from '../types';

export default function SourceNotice({ name, source }: { name: string; source: Source<unknown> }) {
  if (source.ok) return null;
  return (
    <div className='rounded-md border border-accent/40 bg-accent-deep/15 px-4 py-3 text-sm'>
      <p className='font-semibold text-accent-bright'>{name} 데이터를 불러오지 못했습니다</p>
      <p className='mt-1 break-all text-ink-2'>{source.error}</p>
      {source.setup && source.error.includes('.env') && (
        <p className='mt-1 text-ink-3'>
          apps/admin/.env.example 을 apps/admin/.env 로 복사해 값을 채우고 <code>pnpm admin</code>{' '}
          을 다시 실행하세요.
        </p>
      )}
    </div>
  );
}

export function RequestError({ message }: { message: string }) {
  return (
    <div className='rounded-md border border-accent/40 px-4 py-3 text-sm text-accent-bright'>
      {message}
    </div>
  );
}
