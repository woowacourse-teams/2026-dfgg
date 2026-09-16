import { POSITION_LABEL } from '../../../../packages/i18n/common';
import { DESKTOP_TEXT } from '../../../../packages/i18n/desktop';
import { useDict, useLang } from '../../../../packages/i18n/useLang';
import BuildList from '../../components/BuildList';
import ItemBuild from '../../components/ItemBuild';
import { findChampion, useRecommendation } from '../../components/useRecommendation';
import { useRecommendationV3 } from '../../components/useRecommendationV3';
import { useRecommendMode } from '../../components/useRecommendMode';

/**
 * 밴픽 중 롤 클라이언트 위에 얹히는 창.
 * 배경이 투명해야 하므로 바깥 div에 배경색을 주지 않는다 — 카드에만 준다.
 */
export default function App() {
  // 오버레이는 클릭이 통과하는 창이라 버튼을 못 단다. 메인 창에서 고른 모드를
  // 그대로 따라간다. 두 방식 다 항상 돌고 있어서 전환 지연 없이 바로 보인다.
  const [mode] = useRecommendMode();
  const { lang } = useLang();
  const t = useDict(DESKTOP_TEXT);
  const positionLabel = POSITION_LABEL[lang];

  const { lineup, ddragon, result, error, loading, enemyPicked, allyPicked } = useRecommendation();
  const { result: resultV3, error: errorV3, loading: loadingV3 } = useRecommendationV3();

  const myChampion =
    ddragon && lineup ? findChampion(ddragon, lineup.myChampionId, lineup.myChampionName) : null;

  const activeError = mode === 1 ? error : errorV3;
  const activeLoading = mode === 1 ? loading : loadingV3;
  const hasResult = mode === 1 ? Boolean(result) : Boolean(resultV3);

  return (
    <div className='p-2'>
      {/*
        게임 화면을 가리면 안 되므로 카드 배경은 아주 옅게만 쓴다. 블러도 뒤가
        흐려 보이게 만들어 빼고, 대신 글자에 진한 그림자를 줘서 어떤 배경 위에서도
        읽히게 한다.
      */}
      <div className='rounded-xl bg-black/15 p-2.5 ring-1 ring-white/10 [text-shadow:0_1px_2px_rgb(0_0_0/0.95),0_0_6px_rgb(0_0_0/0.8)]'>
        <header className='flex items-baseline justify-between gap-2'>
          <h1 className='truncate text-xs font-bold text-white'>
            {myChampion?.name ?? 'dfgg'}
            {mode === 1 && result && (
              <span className='ml-1 text-[11px] font-normal text-white/80'>
                {positionLabel[result.position] ?? result.position}
              </span>
            )}
            {mode === 2 && resultV3 && (
              <span className='ml-1 text-[11px] font-normal text-white/80'>
                {resultV3.servedBy}
              </span>
            )}
          </h1>
          <span className='shrink-0 text-[10px] text-white/70'>
            {allyPicked}/5 · {enemyPicked}/5
          </span>
        </header>

        {activeLoading && <p className='mt-1.5 text-[11px] text-white/80'>{t.overlay.loading}</p>}

        {activeError && (
          <p className='mt-1.5 text-[11px] font-medium text-rose-300' role='alert'>
            {activeError}
          </p>
        )}

        {!hasResult && !activeLoading && !activeError && (
          <p className='mt-1.5 text-[11px] text-white/80'>
            {!lineup
              ? t.overlay.waitingStart
              : enemyPicked < 5
                ? t.overlay.waitingEnemies
                : t.overlay.analyzing}
          </p>
        )}

        {mode === 1 && (
          <>
            {result && result.builds.every((build) => !build.build?.length) && !loading && (
              <p className='mt-1.5 text-[11px] text-white/80'>{t.overlay.noData}</p>
            )}

            {result &&
              ddragon &&
              !loading &&
              result.builds.some((build) => build.build?.length) && (
                <div className='mt-1.5'>
                  <BuildList
                    builds={result.builds}
                    ddragon={ddragon}
                    compact
                    ownedItemIds={lineup?.myItemIds}
                  />
                </div>
              )}
          </>
        )}

        {mode === 2 && (
          <>
            {resultV3 && resultV3.recommendedItems.length === 0 && !loadingV3 && (
              <p className='mt-1.5 text-[11px] text-white/80'>{t.overlay.noData}</p>
            )}

            {resultV3 && ddragon && !loadingV3 && resultV3.recommendedItems.length > 0 && (
              <div className='mt-1.5'>
                <ItemBuild
                  items={resultV3.recommendedItems}
                  ddragon={ddragon}
                  compact
                  ownedItemIds={lineup?.myItemIds}
                  showRank={false}
                />
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
