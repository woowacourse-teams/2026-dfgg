import { useEffect, useRef, useState } from 'react';

import { canonicalItemId, type DDragonData, itemImageUrl } from '../../../packages/shared/ddragon';
import type { Item } from '../../../packages/shared/types';

interface ItemBuildProps {
  items: Item[];
  ddragon: DDragonData;
  /** 오버레이는 좁으니 작게 그린다. */
  compact?: boolean;
  /** 지금 들고 있는 완성 아이템 id. 이미 산 아이템은 체크 표시로 구분한다. */
  ownedItemIds?: number[];
}

/**
 * compact 모드에서 아이템 한 칸의 폭·간격. BOTTOM 빌드는 7개, 나머지는 6개로
 * 개수가 고정되어 있지 않다(문서 기준). 빌드 하나는 항상 한 줄이어야 한다
 * (builds가 최대 3개라 화면엔 최대 3줄만 생긴다) — 그래서 줄바꿈하는 대신
 * 개수에 맞춰 칸과 간격을 함께 줄여 한 줄에 항상 다 들어가게 한다.
 */
function compactLayout(count: number): { size: string; gap: string } {
  if (count <= 6) return { size: 'w-8', gap: 'gap-1' };
  return { size: 'w-7', gap: 'gap-0.5' };
}

/** 추천 아이템을 순서대로 보여준다. 두 창이 함께 쓴다. */
export default function ItemBuild({
  items,
  ddragon,
  compact = false,
  ownedItemIds = [],
}: ItemBuildProps) {
  // 사용자가 순서를 바꿀 수 있게 로컬 상태로 둔다. 새 추천이 오면(items가 바뀌면)
  // 직전에 바꾼 순서를 버리고 서버가 준 순서로 되돌린다. effect 대신 렌더 중에
  // 바로 맞춰준다 — React가 리렌더를 한 번으로 묶어줘서 깜빡임 없이 처리된다.
  const [prevItems, setPrevItems] = useState(items);
  const [order, setOrder] = useState(items);
  if (items !== prevItems) {
    setPrevItems(items);
    setOrder(items);
  }

  // Shift를 누르고 있는 동안만 드래그로 순서를 바꿀 수 있게 한다. 평소엔 안 눌려서
  // 아이템 칸을 실수로 옮기는 일이 없게 한다.
  // Alt는 OS가 메뉴 포커스 등으로 가로채는 경우가 많아 드래그와 자주 충돌해서 뺐다.
  const [shiftHeld, setShiftHeld] = useState(false);
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Shift') setShiftHeld(true);
    };
    const onKeyUp = (event: KeyboardEvent) => {
      if (event.key === 'Shift') setShiftHeld(false);
    };
    // 창 밖에서 키를 뗀 경우(예: Alt+Tab)까지 대비해 blur에서도 풀어준다.
    const onBlur = () => setShiftHeld(false);
    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    window.addEventListener('blur', onBlur);
    return () => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
      window.removeEventListener('blur', onBlur);
    };
  }, []);

  const dragIndexRef = useRef<number | null>(null);

  const moveItem = (from: number, to: number) => {
    if (from === to) return;
    setOrder((prev) => {
      const next = [...prev];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved);
      return next;
    });
  };

  // 오버레이는 게임 화면을 가리면 안 되니 확실히 작게 그린다.
  const { size, gap } = compact ? compactLayout(order.length) : { size: 'w-14', gap: 'gap-2' };

  // 아레나·아람 등은 같은 아이템도 다른 id로 보고하므로, 원래 id로 정규화해서 비교한다.
  const canonicalOwnedIds = new Set(ownedItemIds.map(canonicalItemId));

  return (
    <ol
      className={
        compact ? `flex flex-nowrap items-start ${gap}` : `flex flex-wrap items-start ${gap}`
      }
    >
      {order.map((item, index) => {
        const owned = canonicalOwnedIds.has(canonicalItemId(item.id));
        return (
          <li
            key={`${item.id}-${index}`}
            className={`${size} shrink-0 text-center`}
            // Shift를 누르고 있을 때만 실제로 옮길 수 있게 한다.
            draggable={shiftHeld}
            onDragStart={(event) => {
              dragIndexRef.current = index;
              event.dataTransfer.effectAllowed = 'move';
              event.dataTransfer.setData('text/plain', String(index));
            }}
            onDragOver={(event) => {
              if (!shiftHeld || dragIndexRef.current === null) return;
              event.preventDefault();
              event.dataTransfer.dropEffect = 'move';
            }}
            onDrop={(event) => {
              if (!shiftHeld || dragIndexRef.current === null) return;
              event.preventDefault();
              moveItem(dragIndexRef.current, index);
              dragIndexRef.current = null;
            }}
            onDragEnd={() => {
              dragIndexRef.current = null;
            }}
          >
            <div
              className={`relative rounded bg-white/10 p-0.5 ${shiftHeld ? 'cursor-grab ring-1 ring-white/30' : ''}`}
            >
              <img
                src={itemImageUrl(ddragon.version, item.id)}
                alt={item.name}
                width={64}
                height={64}
                // 이미 산 아이템은 흐리게 눌러서 아직 안 산 것과 한눈에 구분되게 한다.
                // 배경이 반투명해 게임 화면이 칸 뒤로 살짝 비친다.
                className={`w-full rounded ${owned ? 'opacity-40' : ''}`}
                // Shift로 옮기는 도중 브라우저 기본 이미지 드래그 미리보기가 끼면
                // 우리 드래그 핸들러와 충돌하므로 이미지 자체의 드래그는 막는다.
                draggable={false}
              />
              {owned ? (
                <span
                  aria-label='구매함'
                  className='absolute inset-0 flex items-center justify-center bg-black/40 text-emerald-400'
                >
                  <svg viewBox='0 0 20 20' fill='currentColor' className='size-1/2'>
                    <path
                      fillRule='evenodd'
                      d='M16.704 4.153a.75.75 0 0 1 .143 1.052l-8 10.5a.75.75 0 0 1-1.127.075l-4.5-4.5a.75.75 0 0 1 1.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 0 1 1.05-.143Z'
                      clipRule='evenodd'
                    />
                  </svg>
                </span>
              ) : (
                <span className='absolute top-0 left-0 bg-black/70 px-0.5 text-[9px] leading-tight text-emerald-400'>
                  {index + 1}
                </span>
              )}
            </div>
            {!compact && (
              <p
                className={`mt-1 line-clamp-2 text-[11px] leading-tight ${owned ? 'text-neutral-500 line-through' : 'text-neutral-300'}`}
              >
                {item.name}
              </p>
            )}
          </li>
        );
      })}
    </ol>
  );
}
