import type { ReactNode } from 'react';

interface PanelProps {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}

export default function Panel({
  title,
  description,
  action,
  children,
  className = '',
}: PanelProps) {
  return (
    <section className={`rounded-lg border border-line bg-surface p-5 ${className}`}>
      <div className='mb-4 flex flex-wrap items-start justify-between gap-3'>
        <div>
          <h2 className='text-base font-semibold text-ink'>{title}</h2>
          {description && <p className='mt-1 text-xs text-ink-3'>{description}</p>}
        </div>
        {action}
      </div>
      {children}
    </section>
  );
}

export function Unavailable({ children }: { children: ReactNode }) {
  return (
    <div className='flex min-h-24 items-center justify-center rounded-md border border-dashed border-line px-4 py-6 text-center text-sm text-ink-3'>
      {children}
    </div>
  );
}
