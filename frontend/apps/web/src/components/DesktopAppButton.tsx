import { useNavigate } from 'react-router-dom';

import { useDict } from '../../../../packages/i18n/useLang';
import { NAV_TEXT } from '../../../../packages/i18n/web';

interface DesktopAppButtonProps {
  className?: string;
  data: string;
}

export default function DesktopAppButton({ className = '', data }: DesktopAppButtonProps) {
  const navigate = useNavigate();
  const t = useDict(NAV_TEXT);

  return (
    <button
      data-umami-event={data}
      type='button'
      onClick={() => navigate('/desktop-app')}
      className={`cursor-pointer rounded-xl bg-linear-to-br from-accent-deep to-accent-mid font-bold text-white transition-opacity hover:opacity-90 ${className}`}
    >
      {t.desktopApp}
    </button>
  );
}
