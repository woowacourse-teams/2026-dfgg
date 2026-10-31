import { useEffect, useState } from 'react';

import Dashboard from './pages/Dashboard';
import Players from './pages/Players';

const PERIODS = [7, 30, 90] as const;

const PAGES = [
  { hash: '#/', label: '대시보드' },
  { hash: '#/players', label: '앱 사용자' },
] as const;

type PageHash = (typeof PAGES)[number]['hash'];

// 라우터 없이 해시로 두 페이지를 오간다. 새로고침해도 보던 페이지가 유지된다.
function useHashPage(): PageHash {
  const read = () => (location.hash === '#/players' ? '#/players' : '#/');
  const [page, setPage] = useState<PageHash>(read);
  useEffect(() => {
    const onChange = () => setPage(read());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return page;
}

export default function App() {
  const page = useHashPage();
  const [days, setDays] = useState<number>(30);
  // 새로고침 버튼은 이 값을 올려 같은 기간을 다시 요청하게 한다.
  const [reloadKey, setReloadKey] = useState(0);

  return (
    <div className='min-h-screen'>
      <header className='sticky top-0 z-20 border-b border-line bg-ground/95 backdrop-blur'>
        <div className='mx-auto flex max-w-300 flex-wrap items-center justify-between gap-3 px-6 py-3'>
          <div className='flex items-center gap-6'>
            <div className='flex items-baseline gap-2'>
              <h1 className='font-display text-2xl font-bold'>DFGG</h1>
              <span className='font-display text-sm font-semibold text-ink-2'>Admin</span>
            </div>
            <nav className='flex gap-1'>
              {PAGES.map(({ hash, label }) => (
                <a
                  key={hash}
                  href={hash}
                  aria-current={page === hash ? 'page' : undefined}
                  className={`rounded-md px-3 py-1.5 text-sm ${
                    page === hash
                      ? 'bg-surface-2 font-semibold text-ink'
                      : 'text-ink-2 hover:text-ink'
                  }`}
                >
                  {label}
                </a>
              ))}
            </nav>
          </div>

          <div className='flex items-center gap-3'>
            <div
              className='flex rounded-md border border-line p-0.5'
              role='radiogroup'
              aria-label='기간'
            >
              {PERIODS.map((period) => (
                <button
                  key={period}
                  type='button'
                  role='radio'
                  aria-checked={days === period}
                  onClick={() => setDays(period)}
                  className={`rounded px-3 py-1 text-sm ${
                    days === period ? 'bg-cobalt-deep text-ink' : 'text-ink-2 hover:text-ink'
                  }`}
                >
                  {period}일
                </button>
              ))}
            </div>
            <button
              type='button'
              onClick={() => setReloadKey((n) => n + 1)}
              className='rounded-md border border-line px-3 py-1.5 text-sm text-ink-2 hover:text-ink'
            >
              새로고침
            </button>
          </div>
        </div>
      </header>

      <main className='mx-auto max-w-300 px-6 py-5'>
        {page === '#/players' ? (
          <Players days={days} reloadKey={reloadKey} />
        ) : (
          <Dashboard days={days} reloadKey={reloadKey} />
        )}
      </main>
    </div>
  );
}
