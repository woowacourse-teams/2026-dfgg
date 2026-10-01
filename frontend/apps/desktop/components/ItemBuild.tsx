import { type DDragonData, itemImageUrl } from '../../../packages/shared/ddragon';
import type { Item } from '../../../packages/shared/types';

interface ItemBuildProps {
  items: Item[];
  ddragon: DDragonData;
  /** 오버레이는 좁으니 작게 그린다. */
  compact?: boolean;
}

/** 추천 아이템을 순서대로 보여준다. 두 창이 함께 쓴다. */
export default function ItemBuild({ items, ddragon, compact = false }: ItemBuildProps) {
  // 오버레이는 게임 화면을 가리면 안 되니 확실히 작게 그린다.
  const size = compact ? 'w-8' : 'w-14';

  // 오버레이는 반드시 한 줄이다. 아이템이 8개인 빌드도 있어 줄바꿈을 막고,
  // 폭이 모자라면 넘치는 대신 가로로 스크롤되게 둔다.
  return (
    <ol
      className={
        compact
          ? 'flex flex-nowrap items-start gap-1 overflow-x-auto'
          : 'flex flex-wrap items-start gap-2'
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
