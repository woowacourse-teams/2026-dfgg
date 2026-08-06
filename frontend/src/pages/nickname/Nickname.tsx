import { useState } from 'react';

import banner from '../../assets/dfgg.png';

export default function Nickname() {
  const [nickname, setNickname] = useState('');
  const [item, setItem] = useState<[] | string>([]);
  const [error, setError] = useState('');

  const handleSubmit = () => {
    const getData = async () => {
      try {
        const response = await fetch('/recommendations', {
          method: 'GET',
        });
        const data = await response.json();
        setItem(data);
      } catch (error) {
        console.error(error);
        setError('error');
      }
    };
    getData();
  };

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

      <div className="mx-auto max-w-full px-6 pt-11 pb-13 text-center">
        <h1 className="font-display text-4xl leading-none font-bold text-balance sm:text-6xl">
          다음 판, <em className="text-hextech not-italic">뭘 골라야</em> 이길까
        </h1>

        <p className="mx-auto mt-3 max-w-[42ch] text-ink-2 text-balance">
          현재 나의 게임에 맞는 아이템 6개를 추천해드려요.
        </p>

        <form
          onSubmit={handleSubmit}
          className="chamfer mx-auto mt-7 flex max-w-135 bg-surface-2 shadow-[inset_0_0_0_1px_var(--color-line)] transition-shadow focus-within:shadow-[inset_0_0_0_1px_var(--color-hextech)]"
        >
          <label htmlFor="nickname" className="sr-only">
            소환사 이름
          </label>
          <input
            id="nickname"
            type="text"
            value={nickname}
            onChange={(event) => setNickname(event.target.value)}
            placeholder="소환사 이름 + #태그"
            autoComplete="off"
            className="min-w-0 flex-1 bg-transparent px-4.5 py-4 text-ink outline-none placeholder:text-ink-3"
          />
          <button
            type="submit"
            className="cursor-pointer bg-hextech px-7 font-display text-sm font-bold tracking-[0.16em] text-[#04231f] uppercase transition-colors hover:bg-hextech-bright focus-visible:bg-hextech-bright focus-visible:outline-none"
          >
            분석
          </button>
        </form>
      </div>
    </div>
  );
}
