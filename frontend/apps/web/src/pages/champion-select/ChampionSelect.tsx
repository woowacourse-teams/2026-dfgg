import { type SubmitEvent, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import ChampionCombobox from '../../components/ChampionCombobox';
import { itemImageUrl, useChampions } from '../../hooks/useChampions';
import { type Position, POSITIONS, type RecommendationResponse } from '../../types/recommendation';

const POSITION_LABEL: Record<Position, string> = {
  TOP: '탑',
  JUNGLE: '정글',
  MID: '미드',
  BOTTOM: '원딜',
  SUPPORT: '서폿',
};

type Lineup = Record<Position, string>;

const EMPTY_LINEUP: Lineup = { TOP: '', JUNGLE: '', MID: '', BOTTOM: '', SUPPORT: '' };

export default function ChampionSelect() {
  const [allyLineup, setAllyLineup] = useState<Lineup>(EMPTY_LINEUP);
  const [enemyLineup, setEnemyLineup] = useState<Lineup>(EMPTY_LINEUP);
  const [myPosition, setMyPosition] = useState<Position>('BOTTOM');
  const [result, setResult] = useState<RecommendationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const { champions, version, failed } = useChampions();

  const knownNames = useMemo(() => champions.map((champion) => champion.name), [champions]);

  // 목록을 못 받아왔을 땐 직접 입력만 가능하므로 이름이 채워졌는지만 본다.
  const isValidName = (name: string) => {
    const trimmed = name.trim();
    if (knownNames.length === 0) return trimmed.length > 0;
    return knownNames.indexOf(trimmed) !== -1;
  };

  const filledCount = POSITIONS.reduce(
    (count, position) =>
      count +
      (isValidName(allyLineup[position]) ? 1 : 0) +
      (isValidName(enemyLineup[position]) ? 1 : 0),
    0,
  );
  const isReady = filledCount === 10;

  const handleSubmit = async (event: SubmitEvent) => {
    event.preventDefault();
    if (!isReady) {
      setError('목록에서 챔피언 10명을 모두 선택해 주세요.');
      return;
    }

    // 화면에는 한글로 두되, 백엔드에는 Data Dragon 영문 id로 보낸다.
    // 목록을 못 받아온 경우엔 매칭할 대상이 없으므로 입력값을 그대로 쓴다.
    const toChampion = (position: Position, lineup: Lineup) => {
      const typed = lineup[position].trim();
      const matched = champions.find((champion) => champion.name === typed);
      return { name: matched ? matched.id : typed, position };
    };

    const body = {
      myChampion: toChampion(myPosition, allyLineup),
      allies: POSITIONS.filter((position) => position !== myPosition).map((position) =>
        toChampion(position, allyLineup),
      ),
      enemies: POSITIONS.map((position) => toChampion(position, enemyLineup)),
    };

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
      setError('추천을 불러오지 못했어요. 다시 시도해 주세요.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-dvh bg-ground">
      <div className="mx-auto max-w-4xl px-4 py-6 sm:px-6">
        <Link to="/" className="text-sm text-ink-3 transition-colors hover:text-hextech">
          ← 처음으로
        </Link>

        <div className="mt-4 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <h1 className="font-display text-2xl font-bold sm:text-3xl">
            <em className="text-hextech not-italic">조합</em> 기반 아이템 추천
          </h1>
          <p className="text-sm text-ink-3" aria-live="polite">
            <span className={isReady ? 'text-hextech' : 'text-ink-2'}>{filledCount}</span>
            <span> / 10 입력</span>
          </p>
        </div>

        {failed && (
          <p className="mt-3 text-sm text-loss" role="alert">
            챔피언 목록을 불러오지 못했어요. 이름을 직접 입력해 주세요.
          </p>
        )}

        <form onSubmit={handleSubmit} className="mt-5">
          <div className="flex items-baseline justify-between px-1 pb-1.5 font-display text-xs font-bold tracking-wider">
            <span className="text-win">아군</span>
            <span className="text-loss">상대</span>
          </div>

          <div className="flex flex-col gap-1.5">
            {POSITIONS.map((position) => {
              const isMine = position === myPosition;
              return (
                <div key={position} className="flex items-center gap-1.5">
                  <div className="relative min-w-0 flex-1">
                    <ChampionCombobox
                      value={allyLineup[position]}
                      champions={champions}
                      label={`${isMine ? '내 챔피언' : '아군'} ${POSITION_LABEL[position]}`}
                      accentClass={
                        isMine
                          ? 'shadow-[inset_0_0_0_2px_var(--color-hextech)]'
                          : 'shadow-[inset_0_0_0_1px_var(--color-win)]'
                      }
                      highlighted={isMine}
                      onChange={(name) => setAllyLineup({ ...allyLineup, [position]: name })}
                    />
                    {isMine && (
                      <span className="pointer-events-none absolute top-0 right-1 z-10 font-display text-[10px] font-bold tracking-wider text-hextech">
                        나
                      </span>
                    )}
                  </div>

                  <button
                    type="button"
                    onClick={() => setMyPosition(position)}
                    aria-pressed={isMine}
                    title={`${POSITION_LABEL[position]}를 내 포지션으로`}
                    className={`chamfer-sm h-11 w-13 shrink-0 cursor-pointer font-display text-xs font-bold tracking-wider transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hextech ${
                      isMine
                        ? 'bg-hextech text-[#04231f]'
                        : 'bg-surface-2 text-ink-3 hover:text-ink-2'
                    }`}
                  >
                    {POSITION_LABEL[position]}
                  </button>

                  <div className="min-w-0 flex-1">
                    <ChampionCombobox
                      value={enemyLineup[position]}
                      champions={champions}
                      label={`상대 ${POSITION_LABEL[position]}`}
                      accentClass="shadow-[inset_0_0_0_1px_var(--color-loss)]"
                      onChange={(name) => setEnemyLineup({ ...enemyLineup, [position]: name })}
                    />
                  </div>
                </div>
              );
            })}
          </div>

          <p className="mt-3 text-xs text-ink-3">
            가운데 포지션 버튼을 눌러 내 포지션을 정하세요. 현재{' '}
            <span className="text-hextech">{POSITION_LABEL[myPosition]}</span> 기준으로 추천해요.
          </p>

          <button
            type="submit"
            disabled={loading || !isReady}
            className="chamfer-sm mt-4 w-full cursor-pointer bg-hextech py-3.5 font-display text-sm font-bold tracking-[0.16em] text-[#04231f] uppercase transition-colors hover:bg-hextech-bright focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-hextech disabled:cursor-not-allowed disabled:bg-surface-2 disabled:text-ink-3"
          >
            {loading ? '분석 중...' : '아이템 추천받기'}
          </button>
        </form>

        {error && (
          <p className="mt-4 text-sm text-loss" role="alert">
            {error}
          </p>
        )}

        {loading && (
          <ul className="mt-6 grid grid-cols-6 gap-2" aria-hidden="true">
            {Array.from({ length: 6 }, (_, index) => (
              <li key={index} className="chamfer-sm aspect-square animate-pulse bg-surface-2" />
            ))}
          </ul>
        )}

        {result && !loading && (
          <section className="mt-6" aria-live="polite">
            <h2 className="font-display text-lg font-bold">
              {champions.find((champion) => champion.id === result.champion)?.name ??
                result.champion}
              <span className="ml-2 text-sm font-normal text-ink-3">
                {POSITION_LABEL[result.position] ?? result.position}
              </span>
            </h2>
            <ol className="mt-3 grid grid-cols-6 gap-2">
              {result.items.map((item, index) => (
                <li key={item.id} className="text-center">
                  <div className="chamfer-sm relative bg-surface-2">
                    <img
                      src={version ? itemImageUrl(version, item.id) : undefined}
                      alt={item.name}
                      width={64}
                      height={64}
                      loading="lazy"
                      className="w-full"
                    />
                    <span className="absolute top-0 left-0 bg-ground/80 px-1 font-display text-[10px] text-hextech">
                      {index + 1}
                    </span>
                  </div>
                  <p className="mt-1 line-clamp-2 text-[11px] leading-tight text-ink-2">
                    {item.name}
                  </p>
                </li>
              ))}
            </ol>
          </section>
        )}
      </div>
    </div>
  );
}
