import { ReactNode } from 'react';

export function SettingRow({
  label,
  description,
  children,
}: {
  label: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <div className='setting-row'>
      <div className='setting-column'>
        <span>{label}</span>
        {description && <span className='setting-description'>{description}</span>}
      </div>
      {children}
    </div>
  );
}

export function SettingSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className='setting-section'>
      <h3 className='setting-section-title'>{title}</h3>
      <div className='setting-card'>{children}</div>
    </section>
  );
}
