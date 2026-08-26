import { type DDragonData, itemImageUrl } from '../../../packages/shared/ddragon';
import type { Item } from '../../../packages/shared/types';

interface ItemBuildProps {
  items: Item[];
  ddragon: DDragonData;
  /** 오버레이는 좁으니 작게 그린다. */
  compact?: boolean;
}

/**
 * compact 모드에서 아이템 한 칸의 폭·간격. BOTTOM 빌드는 7개, 나머지는 6개로
 * 개수가 고정되어 있지 않다(문서 기준). 오버레이는 클릭·스크롤을 받지 못하는
 * 클릭-통과 창이라 overflow-x-auto로 숨겨두면 넘친 아이템을 영영 볼 수 없다 —
 * 그래서 개수에 맞춰 칸과 간격을 함께 줄여 한 줄에 항상 다 들어가게 한다.
 */
function compactLayout(count: number): { size: string; gap: string } {
  if (count <= 6) return { size: 'w-8', gap: 'gap-1' };
  return { size: 'w-7', gap: 'gap-0.5' };
}

/** 추천 아이템을 순서대로 보여준다. 두 창이 함께 쓴다. */
export default function ItemBuild({ items, ddragon, compact = false }: ItemBuildProps) {
  // 오버레이는 게임 화면을 가리면 안 되니 확실히 작게 그린다.
  const { size, gap } = compact ? compactLayout(items.length) : { size: 'w-14', gap: 'gap-2' };

  return (
    <ol
      className={
        compact ? `flex flex-nowrap items-start ${gap}` : `flex flex-wrap items-start ${gap}`
      }
    >
      {items.map((item, index) => (
        <li key={`${item.id}-${index}`} className={`${size} shrink-0 text-center`}>
          <div className='relative'>
            <img
              src={itemImageUrl(ddragon.version, item.id)}
              alt={item.name}
              width={64}
              height={64}
              className='w-full rounded'
            />
            <span className='absolute top-0 left-0 bg-black/70 px-0.5 text-[9px] leading-tight text-emerald-400'>
              {index + 1}
            </span>
          </div>
          {!compact && (
            <p className='mt-1 line-clamp-2 text-[11px] leading-tight text-neutral-300'>
              {item.name}
            </p>
          )}
        </li>
      ))}
    </ol>
  );
}
