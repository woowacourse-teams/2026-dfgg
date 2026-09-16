import { FOOTER_TEXT, POSITION_LABEL } from '../../../../packages/i18n/common';
import { DESKTOP_TEXT } from '../../../../packages/i18n/desktop';
import LangToggle from '../../../../packages/i18n/LangToggle';
import { useDict, useLang } from '../../../../packages/i18n/useLang';
import type { DDragonData } from '../../../../packages/shared/ddragon';
import BuildList from '../../components/BuildList';
import ItemBuild from '../../components/ItemBuild';
import OverlayControls from '../../components/OverlayControls';
import UpdateBanner from '../../components/UpdateBanner';
import { useAnalyticsBridge } from '../../components/useAnalyticsBridge';
import { WINDOW_MODE_FULLSCREEN } from '../../components/useLineup';
import { findChampion, isPicked, useRecommendation } from '../../components/useRecommendation';
import { useRecommendationV3 } from '../../components/useRecommendationV3';
import { useRecommendMode } from '../../components/useRecommendMode';
import type { LineupSlot } from '../../electron/types';

/** 메인 프로세스가 기본 브라우저로 열어준다. 앱 창 안에서는 열리지 않는다. */
const PRIVACY_URL = 'https://dfgg.pro/privacy';

/** 피드백은 웹 페이지로만 받는다. 앱 안에 폼을 새로 만들지 않고 그대로 연결한다. */
const FEEDBACK_URL = 'https://www.dfgg.pro/feedback';

/** 서비스를 처음 공개한 해. 해가 바뀌어도 그대로 둔다. */
const COPYRIGHT_YEAR = 2026;

function ChampionRow({
  slots,
  ddragon,
  undecided,
}: {
  slots: LineupSlot[];
  ddragon: DDragonData | null;
  undecided: string;
}) {
  return (
    <ul className='flex gap-2'>
      {slots.map((slot, index) => {
        const champion = ddragon && findChampion(ddragon, slot.championId, slot.championName);
        return (
          <li key={slot.cellId >= 0 ? slot.cellId : index} className='w-16 text-center'>
            {champion ? (
              <img src={champion.imageUrl} alt={champion.name} width={56} height={56} />
            ) : (
              <div className='size-14 rounded bg-neutral-800' />
            )}
            <p className='mt-1 truncate text-[11px] text-neutral-400'>
              {champion?.name ?? undecided}
            </p>
          </li>
        );
      })}
    </ul>
  );
}

export default function App() {
  useAnalyticsBridge();

  const t = useDict(DESKTOP_TEXT);
  const footer = useDict(FOOTER_TEXT);
  const { lang } = useLang();
  const positionLabel = POSITION_LABEL[lang];

  // 1번(빌드 세트)과 2번(구매할 때마다 갱신) 두 방식을 항상 같이 돌려두고,
  // 버튼은 어느 결과를 보여줄지만 바꾼다 — 전환할 때마다 다시 기다리지 않아도 된다.
  // 둘 다 내부에서 useLineup·ddragon을 따로 불러오므로 약간의 중복 호출이 있다.
  // mode 값은 메인 프로세스가 들고 있어서, 여기서 바꾸면 오버레이도 따라간다.
  const [mode, setMode] = useRecommendMode();

  const {
    lineup,
    status,
    windowMode,
    ddragon,
    request,
    result,
    error,
    loading,
    allyPicked,
    enemyPicked,
  } = useRecommendation();

  const { result: resultV3, error: errorV3, loading: loadingV3 } = useRecommendationV3();

  return (
    <div className='min-h-dvh bg-neutral-950 text-neutral-100'>
      {/*
        제목 표시줄을 숨긴 자리. 창 버튼은 오른쪽에 네이티브로 얹히므로 여기서는
        끌 수 있는 영역만 만들어 준다. 높이는 메인 프로세스의 TITLE_BAR.height 와
        같아야 버튼과 어긋나지 않는다.
      */}
      <div className='flex h-10 items-center px-5 [-webkit-app-region:drag]'>
        <span className='text-[11px] font-bold tracking-[0.2em] text-neutral-600 uppercase'>
          dfgg
        </span>
      </div>

      <div className='px-6 pb-6'>
        <header className='flex flex-wrap items-baseline justify-between gap-2'>
          <h1 className='text-xl font-bold'>{t.title}</h1>
          <div className='flex items-baseline gap-3'>
            <LangToggle
              label={t.langLabel}
              activeClass='bg-emerald-500 text-neutral-950'
              inactiveClass='text-neutral-400 hover:text-neutral-200'
            />
            <p className='text-sm text-neutral-400'>{t.status[status]}</p>
            {/* 눌러도 앱 창 안에서 열리지 않고 기본 브라우저로 넘어간다. */}
            <a
              href={FEEDBACK_URL}
              target='_blank'
              rel='noreferrer'
              onClick={() => window.umami?.track('desktop-feedback-click')}
              className='cursor-pointer rounded bg-neutral-800 px-2.5 py-1 text-xs font-bold text-neutral-300 transition-colors hover:bg-neutral-700 hover:text-neutral-100'
            >
              {t.feedback}
            </a>
          </div>
        </header>

        {/* 두 추천 방식은 항상 같이 돌고 있다. 버튼은 결과 표시만 바꾼다. */}
        <div className='mt-3 flex gap-1.5' role='tablist' aria-label={t.modeTablist}>
          {(
            [
              [1, t.mode1],
              [2, t.mode2],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              type='button'
              role='tab'
              aria-selected={mode === value}
              onClick={() => setMode(value)}
              className={`cursor-pointer rounded px-3 py-1.5 text-xs font-bold transition-colors ${
                mode === value
                  ? 'bg-emerald-500 text-neutral-950'
                  : 'bg-neutral-800 text-neutral-400 hover:text-neutral-200'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <UpdateBanner />

        <OverlayControls />

        {/*
        설정은 사용자가 직접 바꾼다. 대신 눌러주는 기능은 롤 클라이언트에 쓰기가
        일어나는 동작이라 Riot 정책상 위험해 두지 않았다.
      */}
        {windowMode === WINDOW_MODE_FULLSCREEN && (
          <div className='mt-4 rounded border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-300'>
            <p>
              {t.fullscreenTitle[0]}
              <strong>{t.fullscreenTitle[1]}</strong>
              {t.fullscreenTitle[2]}
            </p>
            <p className='mt-1.5 text-xs text-amber-300/80'>
              {t.fullscreenHint[0]}
              <strong>{t.fullscreenHint[1]}</strong>
              {t.fullscreenHint[2]}
              <strong>{t.fullscreenHint[3]}</strong>
              {t.fullscreenHint[4]}
            </p>
          </div>
        )}

        {!lineup && <p className='mt-8 text-sm text-neutral-400'>{t.idle}</p>}

        {lineup && (
          <>
            <section className='mt-6 space-y-4'>
              <div>
                <h2 className='mb-1.5 text-xs font-bold tracking-wider text-emerald-400'>
                  {t.allies} {allyPicked} / 5
                </h2>
                <ChampionRow slots={lineup.allies} ddragon={ddragon} undecided={t.undecided} />
              </div>
              <div>
                <h2 className='mb-1.5 text-xs font-bold tracking-wider text-rose-400'>
                  {t.enemies} {enemyPicked} / 5
                </h2>
                <ChampionRow slots={lineup.enemies} ddragon={ddragon} undecided={t.undecided} />
              </div>
            </section>

            {!request && (
              <p className='mt-6 text-sm text-neutral-400'>
                {!lineup.myPosition && t.noPosition}
                {!isPicked({
                  cellId: lineup.myCellId,
                  championId: lineup.myChampionId,
                  championName: lineup.myChampionName,
                  position: null,
                }) && t.pickMine}
                {enemyPicked < 5 && t.waitingEnemies}
                {enemyPicked === 5 && allyPicked < 5 && t.waitingAllies}
              </p>
            )}
          </>
        )}

        {mode === 1 && (
          <>
            {loading && <p className='mt-6 text-sm text-neutral-400'>{t.analyzing}</p>}

            {error && (
              <p className='mt-6 text-sm text-rose-400' role='alert'>
                {error}
              </p>
            )}

            {/* 빌드가 아예 없거나(빈 배열) 다 있는데 아이템(build)이 null인 경우 모두 포함한다. */}
            {result && result.builds.every((build) => !build.build?.length) && !loading && (
              <p className='mt-8 text-sm text-neutral-400'>{t.noData}</p>
            )}

            {result &&
              ddragon &&
              !loading &&
              result.builds.some((build) => build.build?.length) && (
                <section className='mt-8' aria-live='polite'>
                  <h2 className='text-lg font-bold'>
                    {(lineup &&
                      findChampion(ddragon, lineup.myChampionId, lineup.myChampionName)?.name) ??
                      result.champion}
                    <span className='ml-2 text-sm font-normal text-neutral-400'>
                      {positionLabel[result.position] ?? result.position}
                    </span>
                  </h2>
                  <div className='mt-3'>
                    <BuildList
                      builds={result.builds}
                      ddragon={ddragon}
                      ownedItemIds={lineup?.myItemIds}
                    />
                  </div>
                </section>
              )}
          </>
        )}

        {mode === 2 && (
          <>
            {loadingV3 && <p className='mt-6 text-sm text-neutral-400'>{t.analyzing}</p>}

            {errorV3 && (
              <p className='mt-6 text-sm text-rose-400' role='alert'>
                {errorV3}
              </p>
            )}

            {resultV3 && resultV3.recommendedItems.length === 0 && !loadingV3 && (
              <p className='mt-8 text-sm text-neutral-400'>{t.noData}</p>
            )}

            {resultV3 && ddragon && !loadingV3 && resultV3.recommendedItems.length > 0 && (
              <section className='mt-8' aria-live='polite'>
                <h2 className='text-lg font-bold'>
                  {(lineup &&
                    findChampion(ddragon, lineup.myChampionId, lineup.myChampionName)?.name) ??
                    t.recommendedItems}
                  <span className='ml-2 text-sm font-normal text-neutral-400'>
                    {resultV3.servedBy}
                  </span>
                </h2>
                <p className='mt-1 text-xs text-neutral-500'>{t.pickNextCore}</p>
                <div className='mt-3'>
                  <ItemBuild
                    items={resultV3.recommendedItems}
                    ddragon={ddragon}
                    ownedItemIds={lineup?.myItemIds}
                    showRank={false}
                  />
                </div>
              </section>
            )}
          </>
        )}

        <footer className='mt-10 border-t border-neutral-800 pt-4 text-xs text-neutral-500'>
          <p>
            {footer.privacy}{' '}
            {/* 눌러도 기본 브라우저로 열리니, 주소를 그대로 적어 옮겨적을 수도 있게 한다. */}
            <a
              href={PRIVACY_URL}
              target='_blank'
              rel='noreferrer'
              className='text-neutral-400 hover:text-neutral-200 hover:underline'
            >
              {PRIVACY_URL}
            </a>
          </p>
          <p className='mt-2 leading-relaxed'>{footer.disclaimer}</p>
          {/* Riot 이 요구하는 고지문 원문. 의역하지 않고 그대로 둔다. */}
          <p className='mt-1.5 leading-relaxed'>
            dfgg isn&apos;t endorsed by Riot Games and doesn&apos;t reflect the views or opinions of
            Riot Games or anyone officially involved in producing or managing Riot Games properties.
            Riot Games and all associated properties are trademarks or registered trademarks of Riot
            Games, Inc.
          </p>
          {/* 챔피언·아이템 이미지가 Riot 저작물이므로 출처를 함께 남긴다. */}
          <p className='mt-2'>© {COPYRIGHT_YEAR} dfgg. League of Legends © Riot Games, Inc.</p>
        </footer>
      </div>
    </div>
  );
}
