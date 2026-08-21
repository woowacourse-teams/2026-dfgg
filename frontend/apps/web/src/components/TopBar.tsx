import { useNavigate } from 'react-router-dom';

import Logo from '../assets/icon.png';

export default function TopBar() {
  const navigate = useNavigate();

  return (
    <header className='flex flex-row items-center justify-between gap-2 m-2 p-2'>
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
      <div>
        <h2
          onClick={() => navigate('/feedback')}
          className='font-semibold text-lg font-display cursor-pointer'
        >
          피드백
        </h2>
      </div>
    </header>
  );
}
