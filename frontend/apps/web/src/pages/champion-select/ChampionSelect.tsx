import { type SubmitEvent, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';

import ChampionCombobox from '../../components/ChampionCombobox';
import DesktopAppButton from '../../components/DesktopAppButton';
import { champion as CHAMPION_POOL } from '../../data/Champion';
import { itemImageUrl, useChampions } from '../../hooks/useChampions';
import {
  type Position,
  POSITIONS,
  type RecommendationBuild,
  type RecommendationResponse,
  type RecommendationV3Response,
} from '../../types/recommendation';

/** 지금은 랭크 조회 기능이 없어 고정값을 보낸다. */
const TIER = 'PLATINUM';

/** Data Dragon 버전(예: "16.17.1")을 실제 패치 번호("16.17")로 줄인다. */
function toPatch(version: string): string {
  return version.split('.').slice(0, 2).join('.');
}

const POSITION_LABEL: Record<Position, string> = {
  TOP: '탑',
  JUNGLE: '정글',
  MID: '미드',
  BOTTOM: '원딜',
  SUPPORT: '서폿',
};

/** 라인별 후보 챔피언. op.gg 라인별 페이지를 기준으로 손으로 정리한 목록이다. */
const POSITION_POOL: Record<Position, string[]> = {
  TOP: CHAMPION_POOL.top,
  JUNGLE: CHAMPION_POOL.jungle,
  MID: CHAMPION_POOL.mid,
  BOTTOM: CHAMPION_POOL.adc,
  SUPPORT: CHAMPION_POOL.support,
};

/**
 * 라인에 맞는 후보 중 아직 안 쓴 챔피언을 하나 랜덤으로 고른다.
 * 후보가 다 떨어지면(거의 없음) 전체 챔피언 중 안 쓴 아무 이름으로 대신한다.
 */
function pickRandomChampion(position: Position, used: Set<string>): string {
  const pool = POSITION_POOL[position].filter((name) => !used.has(name));
  if (pool.length > 0) return pool[Math.floor(Math.random() * pool.length)];

  const allNames = Object.values(CHAMPION_POOL).flat();
  const fallback = allNames.filter((name) => !used.has(name));
  return fallback.length > 0 ? fallback[Math.floor(Math.random() * fallback.length)] : '';
}

const CHAMPION_TAG_LABEL: Record<string, string> = {
  TANK: '탱커',
  FIGHTER: '전사',
  MAGE: '마법사',
  ASSASSIN: '암살자',
  MARKSMAN: '원거리 딜러',
  SUPPORT: '서포터',
};

// championTag마다 값이 겹치지 않아 태그 구분 없이 하나로 모아도 된다.
const DIRECTION_LABEL: Record<string, string> = {
  PHYSICAL_DAMAGE: '물리 피해 대응',
  MAGIC_DAMAGE: '마법 피해 대응',
  MIXED_DAMAGE: '물리·마법 복합 피해 대응',
  ANTI_TANK: '탱커 대응',
  BURST_SURVIVAL: '순간 피해 생존',
  SUSTAINED_COMBAT: '지속 전투',
  BURST_DAMAGE: '순간 피해',
  SUSTAINED_DAMAGE: '지속 피해',
  SURVIVAL_RESPONSE: '생존 대응',
  BURST_ASSASSINATION: '순간 암살',
  DEFENSE_NEUTRALIZATION: '방어 무력화',
  ENGAGE_SURVIVAL: '진입 후 생존',
  CRITICAL_STRIKE_DAMAGE: '치명타 피해',
  ANTI_TANK_SUSTAINED_DAMAGE: '대탱커 지속 피해',
  SURVIVAL_KITING: '생존 및 카이팅',
  ENGAGE_INITIATION: '전투 개시',
  ALLY_PROTECTION: '아군 보호',
  HEALING_ENHANCEMENT: '회복 및 강화',
};

type Lineup = Record<Position, string>;

const EMPTY_LINEUP: Lineup = { TOP: '', JUNGLE: '', MID: '', BOTTOM: '', SUPPORT: '' };

export default function ChampionSelect() {
  const [allyLineup, setAllyLineup] = useState<Lineup>(EMPTY_LINEUP);
  const [enemyLineup, setEnemyLineup] = useState<Lineup>(EMPTY_LINEUP);
  const [myPosition, setMyPosition] = useState<Position>('TOP');
  const [result, setResult] = useState<RecommendationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // 1번(빌드 세트) / 2번(하나씩 골라 다음 후보 받기) 중 어느 걸 보여줄지.
  const [mode, setMode] = useState<1 | 2>(1);
  const [resultV3, setResultV3] = useState<RecommendationV3Response | null>(null);
  const [loadingV3, setLoadingV3] = useState(false);
  const [errorV3, setErrorV3] = useState('');
  // 2번에서 지금까지 고른 아이템. 이름도 같이 들고 있어야 진행 목록을 보여줄 수 있다.
  const [chosenItems, setChosenItems] = useState<RecommendationBuild[]>([]);
  // 처음 제출한 조합. 폼을 더 건드려도 2번 재요청은 항상 이 조합 기준으로 나가야 한다.
  const [championBody, setChampionBody] = useState<{
    myChampion: { name: string; position: Position };
    allies: { name: string; position: Position }[];
    enemies: { name: string; position: Position }[];
  } | null>(null);

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

  const fetchV2 = async (body: NonNullable<typeof championBody>) => {
    setLoading(true);
    setError('');
    // 직전 결과를 지운다. 이걸 안 지우면 새 요청이 실패했을 때 에러 문구와
    // 지난 판 아이템이 함께 떠서, 지금 조합의 추천인 것처럼 보인다.
    setResult(null);
    try {
      const response = await fetch('/api/recommendations/v2', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      if (!response.ok) {
        window.umami?.track('recommend-fail', { status: response.status });
        throw new Error(String(response.status));
      }
      setResult(await response.json());
      window.umami?.track('recommend-success');
    } catch (error) {
      console.error(error);
      window.umami?.track('recommend-error');
      setError('추천을 불러오지 못했어요. 다시 시도해 주세요.');
    } finally {
      setLoading(false);
    }
  };

  /**
   * 2번 방식. purchasedItemIds가 비어있으면 첫 후보 목록, 하나 골라 넘기면
   * 그걸 반영한 다음 후보 목록을 받는다. body는 항상 처음 제출한 조합 그대로다.
   */
  const fetchV3 = async (body: NonNullable<typeof championBody>, purchasedItemIds: number[]) => {
    setLoadingV3(true);
    setErrorV3('');
    setResultV3(null);
    try {
      const response = await fetch('/api/recommendations/v3', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...body,
          purchasedItemIds,
          tier: TIER,
          patch: version ? toPatch(version) : '',
        }),
      });
      if (!response.ok) {
        window.umami?.track('recommend-v3-fail', { status: response.status });
        throw new Error(String(response.status));
      }
      setResultV3(await response.json());
      window.umami?.track('recommend-v3-success');
    } catch (error) {
      console.error(error);
      window.umami?.track('recommend-v3-error');
      setErrorV3('추천을 불러오지 못했어요. 다시 시도해 주세요.');
    } finally {
      setLoadingV3(false);
    }
  };

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

    if (mode === 1) {
      await fetchV2(body);
      return;
    }

    setChampionBody(body);
    setChosenItems([]);
    await fetchV3(body, []);
  };

  /** 후보 중 하나를 골랐다. 지금까지 고른 것에 더해서 다음 후보를 다시 받는다. */
  const handleChooseItem = (item: RecommendationBuild) => {
    if (!championBody) return;
    const nextChosen = [...chosenItems, item];
    setChosenItems(nextChosen);
    void fetchV3(
      championBody,
      nextChosen.map((chosen) => chosen.id),
    );
  };

  const handleRestartV3 = () => {
    if (!championBody) return;
    setChosenItems([]);
    void fetchV3(championBody, []);
  };

  /** 10명 전체를 라인에 맞는 챔피언으로 한 번에 채운다. 겹치는 챔피언은 없다. */
  const handleRandomChampion = () => {
    const used = new Set<string>();
    const nextAlly: Lineup = { ...EMPTY_LINEUP };
    const nextEnemy: Lineup = { ...EMPTY_LINEUP };

    for (const position of POSITIONS) {
      const allyChampion = pickRandomChampion(position, used);
      if (allyChampion) used.add(allyChampion);
      nextAlly[position] = allyChampion;

      const enemyChampion = pickRandomChampion(position, used);
      if (enemyChampion) used.add(enemyChampion);
      nextEnemy[position] = enemyChampion;
    }

    setAllyLineup(nextAlly);
    setEnemyLineup(nextEnemy);
    window.umami?.track('champion-select-random');
  };

  return (
    <>
      <div className='flex items-start'>
        <Link to='/' className='text-sm text-ink-3 transition-colors hover:text-accent'>
          ← 처음으로
        </Link>
      </div>

      <div className='mt-4 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1'>
        <h1 className='font-display text-2xl font-bold sm:text-3xl'>
          <em className='text-accent not-italic'>조합</em> 기반 아이템 추천
        </h1>
        <p className='text-sm text-ink-3' aria-live='polite'>
          <span className={isReady ? 'text-win' : 'text-ink-2'}>{filledCount}</span>
          <span> / 10 입력</span>
        </p>
      </div>

      {/* 두 방식 다 같은 폼으로 조합을 받는다. 버튼은 다음 제출이 어느 쪽으로 갈지만 정한다. */}
      <div className='mt-4 flex gap-1.5' role='tablist' aria-label='추천 방식'>
        {(
          [
            [1, '1번 · 빌드 추천'],
            [2, '2번 · 하나씩 골라 추천'],
          ] as const
        ).map(([value, label]) => (
          <button
            key={value}
            type='button'
            role='tab'
            aria-selected={mode === value}
            onClick={() => setMode(value)}
            className={`chamfer-sm cursor-pointer px-3 py-1.5 font-display text-xs font-bold tracking-wide transition-colors ${
              mode === value
                ? 'bg-accent-strong text-white'
                : 'bg-surface-2 text-ink-3 hover:text-ink-2'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {/*
          아래 두 열이 각각 아군·상대라는 걸 색으로만 알리고 있었다.
          강조 색을 열 머리글(win/loss)과 맞춰야 문구와 칸이 눈으로 이어진다.
        */}
      <div className='mt-4 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1'>
        <p className='mt-3 text-sm text-ink-2'>
          왼쪽 칸에 <strong className='font-semibold text-win'>아군</strong> 5명, 오른쪽 칸에{' '}
          <strong className='font-semibold text-loss'>상대</strong> 5명을 포지션에 맞춰 입력해
          주세요.
        </p>
        <button
          type='button'
          onClick={handleRandomChampion}
          className='cursor-pointer rounded-xl bg-linear-to-br from-accent-deep to-accent-mid px-4 py-1 font-bold text-white transition-opacity hover:opacity-90'
        >
          랜덤 챔피언 뽑기
        </button>
      </div>
      {failed && (
        <p className='mt-3 text-sm text-loss' role='alert'>
          챔피언 목록을 불러오지 못했어요. 이름을 직접 입력해 주세요.
        </p>
      )}

      <p className='mt-3 text-xs text-ink-3'>
        가운데 포지션 버튼을 눌러 내 포지션을 정하세요. 현재{' '}
        <span className='text-mine'>{POSITION_LABEL[myPosition]}</span> 기준으로 추천해요.
      </p>

      <form onSubmit={handleSubmit} className='mt-5'>
        <div className='flex items-baseline justify-between px-1 pb-1.5 font-display text-xs font-bold tracking-wider'>
          <span className='text-win'>아군</span>
          <span className='text-loss'>상대</span>
        </div>

        <div className='flex flex-col gap-1.5'>
          {POSITIONS.map((position) => {
            const isMine = position === myPosition;
            return (
              <div key={position} className='flex items-center gap-1.5'>
                <div className='relative min-w-0 flex-1'>
                  <ChampionCombobox
                    value={allyLineup[position]}
                    champions={champions}
                    label={`${isMine ? '내 챔피언' : '아군'} ${POSITION_LABEL[position]}`}
                    accentClass={
                      isMine
                        ? 'shadow-[inset_0_0_0_2px_var(--color-mine)]'
                        : 'shadow-[inset_0_0_0_1px_var(--color-win)]'
                    }
                    focusClass={
                      isMine
                        ? 'focus-within:shadow-[inset_0_0_0_2px_var(--color-mine)]'
                        : 'focus-within:shadow-[inset_0_0_0_2px_var(--color-win)]'
                    }
                    highlighted={isMine}
                    onChange={(name) => setAllyLineup({ ...allyLineup, [position]: name })}
                  />
                  {isMine && (
                    <span className='pointer-events-none absolute top-0 right-1 z-10 font-display text-[10px] font-bold tracking-wider text-mine'>
                      나
                    </span>
                  )}
                </div>

                <button
                  type='button'
                  onClick={() => setMyPosition(position)}
                  aria-pressed={isMine}
                  title={`${POSITION_LABEL[position]}를 내 포지션으로`}
                  className={`chamfer-sm h-11 w-13 shrink-0 cursor-pointer font-display text-xs font-bold tracking-wider transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-mine ${
                    isMine ? 'bg-mine text-[#241a04]' : 'bg-surface-2 text-ink-3 hover:text-ink-2'
                  }`}
                >
                  {POSITION_LABEL[position]}
                </button>

                <div className='min-w-0 flex-1'>
                  <ChampionCombobox
                    value={enemyLineup[position]}
                    champions={champions}
                    label={`상대 ${POSITION_LABEL[position]}`}
                    accentClass='shadow-[inset_0_0_0_1px_var(--color-loss)]'
                    focusClass='focus-within:shadow-[inset_0_0_0_2px_var(--color-loss)]'
                    onChange={(name) => setEnemyLineup({ ...enemyLineup, [position]: name })}
                  />
                </div>
              </div>
            );
          })}
        </div>

        <button
          data-umami-event='recommend-item'
          type='submit'
          disabled={loading || loadingV3 || !isReady}
          className='chamfer-sm mt-4 w-full cursor-pointer bg-accent-strong py-3.5 font-display text-sm font-bold tracking-[0.16em] text-white uppercase transition-colors hover:bg-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:bg-surface-2 disabled:text-ink-3'
        >
          {loading || loadingV3 ? '분석 중...' : '아이템 추천받기'}
        </button>
      </form>

      {mode === 1 && error && (
        <p className='mt-4 text-sm text-loss' role='alert'>
          {error}
        </p>
      )}

      {mode === 1 && loading && (
        <ul className='mt-6 grid grid-cols-6 gap-2' aria-hidden='true'>
          {Array.from({ length: 6 }, (_, index) => (
            <li key={index} className='chamfer-sm aspect-square animate-pulse bg-surface-2' />
          ))}
        </ul>
      )}

      {mode === 1 && result && !loading && (
        <section className='mt-6' aria-live='polite'>
          <h2 className='font-display text-lg font-bold'>
            {result.champion}{' '}
            <span className='ml-2 text-sm font-normal text-ink-3'>
              {POSITION_LABEL[result.position] ?? result.position}
            </span>
          </h2>
          {/*
              스펙엔 build가 null이 아니라고 돼 있지만, 실제 응답은 아이템이 없는
              빌드에 build: null을 준다. 그대로 .map을 부르면 전체 화면이 죽어서
              다른 챔피언도 못 누르게 되므로 방어적으로 걸러낸다.
            */}
          {result.builds.every((buildInfo) => !buildInfo.build?.length) ? (
            <p className='mt-3 text-sm text-ink-3'>
              이 조합에 맞는 추천 빌드를 아직 찾지 못했어요.
            </p>
          ) : (
            result.builds
              .filter((buildInfo) => buildInfo.build?.length)
              .map((buildInfo, buildIndex) => (
                <div key={buildIndex} className='mt-4 first:mt-3'>
                  <p className='text-xs text-ink-3'>
                    {CHAMPION_TAG_LABEL[buildInfo.championTag] ?? buildInfo.championTag} ·{' '}
                    {DIRECTION_LABEL[buildInfo.direction] ?? buildInfo.direction}
                  </p>
                  <ol className='mt-2 grid grid-cols-6 gap-2'>
                    {(buildInfo.build ?? []).map((item, index) => (
                      <li key={`${buildIndex}-${item.id}`} className='text-center'>
                        <div className='chamfer-sm relative bg-surface-2'>
                          <img
                            src={version ? itemImageUrl(version, item.id) : undefined}
                            alt={item.name}
                            width={64}
                            height={64}
                            loading='lazy'
                            className='w-full'
                          />
                          <span className='absolute top-0 left-0 bg-ground/80 px-1 font-display text-[10px] text-accent'>
                            {index + 1}
                          </span>
                        </div>
                        <p className='mt-1 line-clamp-2 text-[11px] leading-tight text-ink-2'>
                          {item.name}
                        </p>
                      </li>
                    ))}
                  </ol>
                </div>
              ))
          )}
        </section>
      )}

      {mode === 2 && errorV3 && (
        <p className='mt-4 text-sm text-loss' role='alert'>
          {errorV3}
        </p>
      )}

      {mode === 2 && loadingV3 && (
        <ul className='mt-6 grid grid-cols-6 gap-2' aria-hidden='true'>
          {Array.from({ length: 6 }, (_, index) => (
            <li key={index} className='chamfer-sm aspect-square animate-pulse bg-surface-2' />
          ))}
        </ul>
      )}

      {mode === 2 && resultV3 && !loadingV3 && (
        <section className='mt-6' aria-live='polite'>
          <div className='flex flex-wrap items-baseline justify-between gap-2'>
            <h2 className='font-display text-lg font-bold'>
              다음 코어템 후보
              <span className='ml-2 text-sm font-normal text-ink-3'>{resultV3.servedBy}</span>
            </h2>
            {chosenItems.length > 0 && (
              <button
                type='button'
                onClick={handleRestartV3}
                className='cursor-pointer text-xs text-ink-3 underline decoration-dotted hover:text-accent'
              >
                처음부터 다시 고르기
              </button>
            )}
          </div>

          {chosenItems.length > 0 && (
            <div className='mt-2 flex flex-wrap items-center gap-1.5'>
              <span className='text-xs text-ink-3'>지금까지 고른 아이템</span>
              {chosenItems.map((item, index) => (
                <div key={`${item.id}-${index}`} className='chamfer-sm bg-surface-2 p-0.5'>
                  <img
                    src={version ? itemImageUrl(version, item.id) : undefined}
                    alt={item.name}
                    title={item.name}
                    width={28}
                    height={28}
                    className='size-7'
                  />
                </div>
              ))}
            </div>
          )}

          {resultV3.recommendedItems.length === 0 ? (
            <p className='mt-3 text-sm text-ink-3'>이 조합은 아직 데이터가 부족해요.</p>
          ) : (
            <>
              <p className='mt-3 text-xs text-ink-3'>
                이 중 하나를 클릭해서 다음 코어템으로 고르세요. 순서가 아니라 후보예요. 아이템에
                마우스를 올리면 이름이 보여요.
              </p>
              <ol className='mt-2 flex flex-wrap gap-2'>
                {resultV3.recommendedItems.map((item) => (
                  <li key={item.id} className='w-14 text-center'>
                    <button
                      type='button'
                      onClick={() => handleChooseItem(item)}
                      title={item.name}
                      className='chamfer-sm w-full cursor-pointer bg-surface-2 transition-colors hover:ring-2 hover:ring-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'
                    >
                      <img
                        src={version ? itemImageUrl(version, item.id) : undefined}
                        alt={item.name}
                        width={56}
                        height={56}
                        loading='lazy'
                        className='w-full'
                      />
                    </button>
                    <p className='mt-1 line-clamp-2 text-[10px] leading-tight text-ink-2'>
                      {item.name}
                    </p>
                  </li>
                ))}
              </ol>
            </>
          )}
        </section>
      )}

      {(result || resultV3) && (
        <aside className='chamfer mt-6 flex flex-wrap items-center justify-between gap-4 bg-surface-2 p-5 text-left shadow-[inset_0_0_0_1px_var(--color-line)]'>
          <div>
            <p className='font-display font-bold'>추천 템트리 괜찮으셨나요?</p>
            <p className='mt-1.5 max-w-[46ch] text-sm text-ink-2'>
              데스크톱 앱은 롤 클라이언트에서 조합을 자동으로 읽어옵니다.
              <br />
              다음 판부터는 입력하지 않아도 됩니다.
            </p>
          </div>
          <DesktopAppButton data='desktop-app-champion-select' className='shrink-0 px-5 py-2.5' />
        </aside>
      )}
    </>
  );
}
