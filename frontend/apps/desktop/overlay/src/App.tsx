import type { Position } from '../../../../packages/shared/types';
import BuildList from '../../components/BuildList';
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
    <div className='p-2'>
      {/*
        게임 화면 위에서도 카드 경계가 또렷하도록 반투명 검정 + 블러를 쓴다.
        빌드가 최대 3개까지 세로로 늘어서므로 흰 반투명 배경(bg-white/10)보다
        어두운 배경이 밝은 게임 장면에서도 안정적으로 대비된다.
      */}
      <div className='rounded-xl bg-black/55 p-2.5 shadow-[0_8px_24px_rgb(0_0_0/0.5)] ring-1 ring-white/15 backdrop-blur-md [text-shadow:0_1px_2px_rgb(0_0_0/0.95),0_0_6px_rgb(0_0_0/0.8)]'>
        <header className='flex items-baseline justify-between gap-2'>
          <h1 className='truncate text-xs font-bold text-white'>
            {myChampion?.name ?? 'dfgg'}
            {result && (
              <span className='ml-1 text-[11px] font-normal text-white/80'>
                {POSITION_LABEL[result.position] ?? result.position}
              </span>
            )}
          </h1>
          <span className='shrink-0 text-[10px] text-white/70'>
            {allyPicked}/5 · {enemyPicked}/5
          </span>
        </header>

        {loading && <p className='mt-1.5 text-[11px] text-white/80'>분석 중...</p>}

        {error && (
          <p className='mt-1.5 text-[11px] font-medium text-rose-300' role='alert'>
            {error}
          </p>
        )}

        {!result && !loading && !error && (
          <p className='mt-1.5 text-[11px] text-white/80'>
            {!lineup
              ? '밴픽이나 게임이 시작되면 표시돼요'
              : enemyPicked < 5
                ? '상대 챔피언을 기다리는 중'
                : '조합을 분석하는 중'}
          </p>
        )}

        {result && result.builds.length === 0 && !loading && (
          <p className='mt-1.5 text-[11px] text-white/80'>이 조합엔 추천할 빌드가 아직 없어요</p>
        )}

        {result && ddragon && !loading && result.builds.length > 0 && (
          <div className='mt-1.5'>
            <BuildList builds={result.builds} ddragon={ddragon} compact />
          </div>
        )}
      </div>
    </div>
  );
}
