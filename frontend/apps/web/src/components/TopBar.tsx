import { useNavigate } from 'react-router-dom';

import LangToggle from '../../../../packages/i18n/LangToggle';
import { useDict } from '../../../../packages/i18n/useLang';
import { NAV_TEXT } from '../../../../packages/i18n/web';
import Logo from '../assets/icon.png';
import DesktopAppButton from './DesktopAppButton';

export default function TopBar() {
  const navigate = useNavigate();
  const t = useDict(NAV_TEXT);

  return (
    <header className='fixed-header flex flex-row items-center justify-between gap-2'>
      <div
        onClick={() => navigate('/')}
        className='flex flex-row items-center justify-center gap-2 cursor-pointer'
      >
        <img src={Logo} alt='dfgg logo' className='w-12 h-auto' />
        <div className='flex items-baseline'>
          <h1 className='font-display font-bold text-3xl'>DFGG</h1>
          <span className='px-1 font-display text-xs font-semibold text-red-300'>Beta</span>
        </div>
      </div>
      <div className='flex flex-row items-center gap-4 sm:gap-8'>
        <LangToggle
          label={t.langLabel}
          activeClass='bg-accent-strong text-white'
          inactiveClass='text-ink-3 hover:text-ink-2'
        />
        <h2
          onClick={() => navigate('/feedback')}
          className='font-semibold text-base font-display cursor-pointer'
        >
          {t.feedback}
        </h2>
        <DesktopAppButton data='desktop-app-champion-topbar' className='px-4 py-2' />
      </div>
    </header>
  );
}
