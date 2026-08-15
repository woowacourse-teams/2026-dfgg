import type { Position } from '../../../../packages/shared/types';
import ItemBuild from '../../components/ItemBuild';
import { findChampion, useRecommendation } from '../../components/useRecommendation';

const POSITION_LABEL: Record<Position, string> = {
  TOP: '탑',
  JUNGLE: '정글',
  MID: '미드',
  BOTTOM: '원딜',
  SUPPORT: '서폿',
};

/**
 * 밴픽 중 롤 클라이언트 위에 얹히는 창.
 * 배경이 투명해야 하므로 바깥 div에 배경색을 주지 않는다 — 카드에만 준다.
 */
export default function App() {
  const { lineup, ddragon, result, error, loading, enemyPicked, allyPicked } = useRecommendation();

  const myChampion =
    ddragon && lineup ? findChampion(ddragon, lineup.myChampionId, lineup.myChampionName) : null;

  return (
    <div className="p-2">
      <div className="rounded-lg bg-neutral-950/90 p-2.5 shadow-lg ring-1 ring-white/10">
        <header className="flex items-baseline justify-between gap-2">
          <h1 className="truncate text-xs font-bold text-neutral-100">
            {myChampion?.name ?? 'dfgg'}
            {result && (
              <span className="ml-1 text-[11px] font-normal text-neutral-400">
                {POSITION_LABEL[result.position] ?? result.position}
              </span>
            )}
          </h1>
          <span className="shrink-0 text-[10px] text-neutral-500">
            {allyPicked}/5 · {enemyPicked}/5
          </span>
        </header>

        {loading && <p className="mt-1.5 text-[11px] text-neutral-400">분석 중...</p>}

        {error && (
          <p className="mt-1.5 text-[11px] text-rose-400" role="alert">
            {error}
          </p>
        )}

        {!result && !loading && !error && (
          <p className="mt-1.5 text-[11px] text-neutral-400">
            {!lineup
              ? '밴픽이나 게임이 시작되면 표시돼요'
              : enemyPicked < 5
                ? '상대 챔피언을 기다리는 중'
                : '조합을 분석하는 중'}
          </p>
        )}

        {result && ddragon && !loading && (
          <div className="mt-1.5">
            <ItemBuild items={result.items} ddragon={ddragon} compact />
          </div>
        )}
      </div>
    </div>
  );
}
