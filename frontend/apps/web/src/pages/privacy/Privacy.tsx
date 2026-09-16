import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

import { useDict } from '../../../../../packages/i18n/useLang';
import { PRIVACY_TEXT } from '../../../../../packages/i18n/web';
import { PRIVACY_URL } from '../../components/SiteFooter';

const CONTACT_EMAIL = 'dfgg0821@gmail.com';

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className='mt-10'>
      <h2 className='font-display text-xl font-bold'>{title}</h2>
      <div className='mt-3 space-y-3 text-sm leading-relaxed text-ink-2'>{children}</div>
    </section>
  );
}

export default function Privacy() {
  const t = useDict(PRIVACY_TEXT);

  return (
    <div className='min-h-screen bg-ground'>
      <div className='mx-auto max-w-180 px-6 pt-11 pb-16'>
        <Link to='/' className='font-display text-sm tracking-wider text-ink-3 hover:text-accent'>
          {t.back}
        </Link>

        <h1 className='mt-6 font-display text-4xl leading-none font-bold'>{t.title}</h1>
        <p className='mt-3 text-sm text-ink-3'>
          {PRIVACY_URL} · {t.lastUpdatedLabel}: {t.lastUpdated}
        </p>

        <p className='mt-6 text-sm leading-relaxed text-ink-2'>{t.scope}</p>

        {t.sections.map((section) => (
          <Section key={section.title} title={section.title}>
            {section.body.map((paragraph) => (
              <p key={paragraph}>{paragraph}</p>
            ))}
          </Section>
        ))}

        <Section title={t.contactTitle}>
          <p>
            {t.contactLabel}
            <a href={`mailto:${CONTACT_EMAIL}`} className='text-accent hover:underline'>
              {CONTACT_EMAIL}
            </a>
          </p>
        </Section>

        <Section title={t.noticeTitle}>
          <p>{t.notice}</p>
          {/* Riot 이 요구하는 고지문 원문. 의역하지 않고 그대로 둔다. */}
          <p className='text-ink-3'>
            DFGG isn&apos;t endorsed by Riot Games and doesn&apos;t reflect the views or opinions of
            Riot Games or anyone officially involved in producing or managing Riot Games properties.
            Riot Games and all associated properties are trademarks or registered trademarks of Riot
            Games, Inc.
          </p>
          <p className='text-ink-3'>&copy; 2026 DFGG. League of Legends &copy; Riot Games, Inc.</p>
        </Section>
      </div>
    </div>
  );
}
