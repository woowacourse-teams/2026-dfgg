import { Link } from 'react-router-dom';

import banner from '../../assets/dfgg.png';

const ENTRIES = [
  {
    to: '/nickname',
    title: '닉네임으로 추천받기',
    description: '소환사 이름을 입력하면 최근 전적을 바탕으로 아이템을 추천해드려요.',
  },
  {
    to: '/champion-select',
    title: '챔피언 조합으로 추천받기',
    description: '지금 밴픽 중인 10명의 챔피언을 입력하면 아이템을 추천해드려요.',
  },
];

export default function Home() {
  return (
    <div className="min-h-screen bg-ground">
      <div className="relative aspect-5/1 overflow-hidden bg-cornflower">
        <img
          src={banner}
          alt="DFGG"
          className="absolute inset-0 h-full w-full object-cover object-[center_49%]"
        />
        <div className="banner-veil pointer-events-none absolute inset-0" />
      </div>

      <div className="mx-auto max-w-200 px-6 pt-11 pb-13 text-center">
        <h1 className="font-display text-4xl leading-none font-bold text-balance sm:text-6xl">
          다음 판, <em className="text-hextech not-italic">뭘 골라야</em> 이길까
        </h1>
        <p className="mx-auto mt-3 max-w-[42ch] text-ink-2 text-balance">
          어떤 방식으로 추천받을지 골라주세요.
        </p>

        <nav className="mt-9 grid gap-4 text-left sm:grid-cols-2">
          {ENTRIES.map((entry) => (
            <Link
              key={entry.to}
              to={entry.to}
              className="chamfer group bg-surface-2 p-6 shadow-[inset_0_0_0_1px_var(--color-line)] transition-shadow hover:shadow-[inset_0_0_0_1px_var(--color-hextech)]"
            >
              <h2 className="font-display text-xl font-bold transition-colors group-hover:text-hextech">
                {entry.title}
              </h2>
              <p className="mt-2 text-sm text-ink-2">{entry.description}</p>
            </Link>
          ))}
        </nav>
      </div>
    </div>
  );
}
