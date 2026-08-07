import { type SubmitEvent, useState } from 'react';
import { Link } from 'react-router-dom';

import banner from '../../assets/dfgg.png';

interface Item {
  id: string;
  name: string;
  imageUrl: string;
}

export default function Nickname() {
  const [nickname, setNickname] = useState('');
  const [item, setItem] = useState<Item[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = (event: SubmitEvent) => {
    event.preventDefault();
    const query = nickname.trim();
    const [gameName, tagLine] = query.split('#');

    if (!gameName || !tagLine) {
      setError('소환사 이름을 "이름#태그" 형식으로 입력해 주세요.');
      return;
    }

    setLoading(true);
    setError('');
    const getData = async ({ gameName, tagLine }: { gameName: string; tagLine: string }) => {
      try {
        const response = await fetch(
          `/recommendations?gameName=${encodeURIComponent(gameName)}&tagLine=${encodeURIComponent(tagLine)}`,
          {
            method: 'GET',
          },
        );
        if (!response.ok) throw new Error(String(response.status));
        const data = await response.json();
        setItem(data);
      } catch (error) {
        console.error(error);
        setError('데이터 로드 중 에러 발생');
      } finally {
        setLoading(false);
      }
    };
    getData({ gameName, tagLine });
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
        <Link
          to="/"
          className="inline-block text-sm text-ink-3 transition-colors hover:text-hextech"
        >
          ← 처음으로
        </Link>

        <h1 className="mt-6 font-display text-4xl leading-none font-bold text-balance sm:text-6xl">
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
      {loading && <p>로딩 중...</p>}
      {error && (
        <>
          <p>{error}</p>
        </>
      )}
      {item.length === 0 && (
        <p>
          {item.map((value) => {
            return <p key={value.id}>{value.name}</p>;
          })}
        </p>
      )}
    </div>
  );
}
