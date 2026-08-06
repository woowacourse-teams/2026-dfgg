import { type SubmitEvent, useState } from 'react';
import { Link } from 'react-router-dom';

import {
  type Champion,
  type Position,
  POSITIONS,
  type RecommendationResponse,
} from '../../types/recommendation';

const ALLY_COUNT = 4;
const ENEMY_COUNT = 5;

const emptyChampion = (position: Position): Champion => ({ name: '', position });

const createTeam = (size: number) =>
  Array.from({ length: size }, (_, index) => emptyChampion(POSITIONS[index % POSITIONS.length]));

export default function ChampionSelect() {
  const [myChampion, setMyChampion] = useState<Champion>(emptyChampion('BOTTOM'));
  const [allies, setAllies] = useState<Champion[]>(() => createTeam(ALLY_COUNT));
  const [enemies, setEnemies] = useState<Champion[]>(() => createTeam(ENEMY_COUNT));
  const [result, setResult] = useState<RecommendationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const updateAt = (team: Champion[], index: number, patch: Partial<Champion>): Champion[] =>
    team.map((champion, i) => (i === index ? { ...champion, ...patch } : champion));

  const handleSubmit = async (event: SubmitEvent) => {
    event.preventDefault();

    const trim = (champion: Champion): Champion => ({ ...champion, name: champion.name.trim() });
    const body = {
      myChampion: trim(myChampion),
      allies: allies.map(trim),
      enemies: enemies.map(trim),
    };

    const isFilled = [body.myChampion, ...body.allies, ...body.enemies].every(
      (champion) => champion.name,
    );
    if (!isFilled) {
      setError('내 챔피언과 아군 4명, 상대 5명을 모두 입력해 주세요.');
      return;
    }

    setLoading(true);
    setError('');
    try {
      const response = await fetch('/recommendations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!response.ok) throw new Error(String(response.status));
      setResult(await response.json());
    } catch (error) {
      console.error(error);
      setError('데이터 로드 중 에러 발생');
    } finally {
      setLoading(false);
    }
  };

  const renderRow = (
    champion: Champion,
    label: string,
    onChange: (patch: Partial<Champion>) => void,
  ) => (
    <div className="flex gap-2">
      <input
        type="text"
        value={champion.name}
        onChange={(event) => onChange({ name: event.target.value })}
        placeholder={label}
        aria-label={`${label} 챔피언 이름`}
        autoComplete="off"
        className="chamfer-sm min-w-0 flex-1 bg-surface-2 px-4 py-3 text-ink shadow-[inset_0_0_0_1px_var(--color-line)] outline-none transition-shadow placeholder:text-ink-3 focus:shadow-[inset_0_0_0_1px_var(--color-hextech)]"
      />
      <select
        value={champion.position}
        onChange={(event) => onChange({ position: event.target.value as Position })}
        aria-label={`${label} 포지션`}
        className="chamfer-sm cursor-pointer bg-surface-2 px-3 py-3 font-display text-sm tracking-wide text-ink-2 shadow-[inset_0_0_0_1px_var(--color-line)] outline-none focus:shadow-[inset_0_0_0_1px_var(--color-hextech)]"
      >
        {POSITIONS.map((position) => (
          <option key={position} value={position}>
            {position}
          </option>
        ))}
      </select>
    </div>
  );

  return (
    <div className="min-h-screen bg-ground">
      <div className="mx-auto max-w-200 px-6 pt-11 pb-13">
        <Link to="/" className="text-sm text-ink-3 transition-colors hover:text-hextech">
          ← 처음으로
        </Link>

        <h1 className="mt-6 font-display text-4xl leading-none font-bold text-balance sm:text-5xl">
          이 <em className="text-hextech not-italic">조합</em>에선 뭘 골라야 이길까
        </h1>
        <p className="mt-3 max-w-[42ch] text-ink-2 text-balance">
          내 챔피언과 아군 4명, 상대 5명을 입력하면 아이템 6개를 추천해드려요.
        </p>

        <form onSubmit={handleSubmit} className="mt-8 flex flex-col gap-7">
          <fieldset>
            <legend className="font-display text-sm font-bold tracking-[0.16em] text-hextech uppercase">
              내 챔피언
            </legend>
            <div className="mt-3">
              {renderRow(myChampion, '내 챔피언', (patch) =>
                setMyChampion({ ...myChampion, ...patch }),
              )}
            </div>
          </fieldset>

          <div className="grid gap-6 sm:grid-cols-2">
            <fieldset>
              <legend className="font-display text-sm font-bold tracking-[0.16em] text-win uppercase">
                아군
              </legend>
              <div className="mt-3 flex flex-col gap-2">
                {allies.map((champion, index) => (
                  <div key={index}>
                    {renderRow(champion, `아군 ${index + 1}`, (patch) =>
                      setAllies(updateAt(allies, index, patch)),
                    )}
                  </div>
                ))}
              </div>
            </fieldset>

            <fieldset>
              <legend className="font-display text-sm font-bold tracking-[0.16em] text-loss uppercase">
                상대
              </legend>
              <div className="mt-3 flex flex-col gap-2">
                {enemies.map((champion, index) => (
                  <div key={index}>
                    {renderRow(champion, `상대 ${index + 1}`, (patch) =>
                      setEnemies(updateAt(enemies, index, patch)),
                    )}
                  </div>
                ))}
              </div>
            </fieldset>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="chamfer-sm cursor-pointer bg-hextech py-4 font-display text-sm font-bold tracking-[0.16em] text-[#04231f] uppercase transition-colors hover:bg-hextech-bright focus-visible:bg-hextech-bright focus-visible:outline-none disabled:opacity-50"
          >
            분석
          </button>
        </form>

        {loading && <p className="mt-6 text-ink-2">로딩 중...</p>}
        {error && <p className="mt-6 text-loss">{error}</p>}
        {result && (
          <section className="mt-8">
            <h2 className="font-display text-lg font-bold">
              {result.champion}
              <span className="ml-2 text-sm text-ink-3">{result.position}</span>
            </h2>
            <ul className="mt-4 grid grid-cols-3 gap-3 sm:grid-cols-6">
              {result.items.map((item) => (
                <li key={item.id} className="text-center">
                  <img src={`/${item.imageUrl}`} alt="" className="chamfer-sm w-full" />
                  <p className="mt-2 text-sm text-ink-2">{item.name}</p>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </div>
  );
}
