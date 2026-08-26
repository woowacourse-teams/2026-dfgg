import { type DDragonData } from '../../../packages/shared/ddragon';
import { type Build, DIRECTION_LABEL } from '../../../packages/shared/types';
import ItemBuild from './ItemBuild';

interface BuildListProps {
  builds: Build[];
  ddragon: DDragonData;
  /** 오버레이는 좁으니 작게 그린다. */
  compact?: boolean;
}

/** 빌드 방향마다 다른 색으로 구분한다. 색만으로 구분하지 않도록 라벨을 항상 같이 보여준다. */
const ACCENT_BY_RANK = ['text-rose-300', 'text-sky-300', 'text-amber-300'];
const DOT_BY_RANK = ['bg-rose-400', 'bg-sky-400', 'bg-amber-400'];

/**
 * 추천 빌드 최대 3개를 순위대로 나열한다. 오버레이가 클릭을 못 받으므로
 * (게임 클릭이 새면 안 된다) 탭이나 화살표로 넘기지 않고 전부 동시에 보여준다.
 */
export default function BuildList({ builds, ddragon, compact = false }: BuildListProps) {
  if (builds.length === 0) return null;

  return (
    <div className={compact ? 'space-y-1.5' : 'space-y-3'}>
      {builds.map((build, rank) => (
        <div
          key={`${build.championTag}-${build.direction}`}
          className={
            compact
              ? 'rounded-lg bg-white/[0.04] p-1.5 ring-1 ring-white/[0.06]'
              : 'rounded-lg bg-neutral-900/60 p-2.5 ring-1 ring-neutral-800'
          }
        >
          <div className='flex items-center gap-1.5'>
            <span
              className={`${compact ? 'h-1.5 w-1.5' : 'h-2 w-2'} shrink-0 rounded-full ${DOT_BY_RANK[rank]}`}
            />
            <p
              className={`truncate font-bold tracking-wide uppercase ${ACCENT_BY_RANK[rank]} ${compact ? 'text-[9.5px]' : 'text-xs'}`}
            >
              {DIRECTION_LABEL[build.direction]}
            </p>
          </div>
          <div className={compact ? 'mt-1' : 'mt-1.5'}>
            <ItemBuild items={build.build} ddragon={ddragon} compact={compact} />
          </div>
        </div>
      ))}
    </div>
  );
}
