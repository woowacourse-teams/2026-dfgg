import './style.css';

import { useEffect, useLayoutEffect, useRef, useState } from 'react';

import type { NamedEntry, RecommendedItem } from '../../shared/types';
import { ChevronIcon } from '../icons';

type Variant = 'ally' | 'counter';

const VARIANT_LABEL: Record<Variant, string> = {
  ally: '시너지',
  counter: '카운터',
};

// 초상화에 붙는 설명. 챔피언 이름과 관계를 한 줄에 담는다.
const VARIANT_RELATION: Record<Variant, string> = {
  ally: '아군 — 시너지 좋음',
  counter: '상대 — 카운터로 좋음',
};

/** 순위 숫자를 붙이는 개수. 그 아래는 자리로만 순서를 알린다. */
const TOP_COUNT = 3;
/** 한 판에 채우는 아이템 칸 수 */
const BUILD_SLOTS = 6;

/**
 * 챔피언 이름은 안 띄우고 초상화만 보여준다.
 * 아군은 초록, 상대는 빨강 테로 가르고 withLabel 이면 짧은 이름표도 붙인다.
 */
function Faces({
  champions,
  variant,
  withLabel = false,
}: {
  champions: NamedEntry[];
  variant: Variant;
  withLabel?: boolean;
}) {
  if (champions.length === 0) return null;

  return (
    <span className={`faces faces-${variant}`}>
      {withLabel && <span className='faces-label'>{VARIANT_LABEL[variant]}</span>}
      {champions.map((champion) => (
        // 이름을 안 띄우므로 alt·title 로 정보를 남긴다.
        <img
          key={champion.id}
          src={champion.imageUrl}
          alt={`${champion.name} — ${VARIANT_RELATION[variant]}`}
          title={`${champion.name} — ${VARIANT_RELATION[variant]}`}
        />
      ))}
    </span>
  );
}

function Pick({ item, rank }: { item: RecommendedItem; rank: number }) {
  const { traits, ally, counter } = item.description;
  const isBest = rank === 1;

  return (
    <li
      className={`pick ${isBest ? 'pick-best' : ''} ${rank > TOP_COUNT ? 'pick-rest' : ''}`}
      style={{ '--order': rank - 1 } as React.CSSProperties}
    >
      <span className='pick-art'>
        <img src={item.imageUrl} alt='' />
        {rank <= TOP_COUNT && (
          <b className='pick-rank' aria-label={`추천 ${rank}순위`}>
            {rank}
          </b>
        )}
      </span>

      <span className='pick-body'>
        <span className='pick-name'>{item.name}</span>
        {traits.length > 0 && (
          <span className='pick-traits' title={traits.join(' · ')}>
            {traits.join(' · ')}
          </span>
        )}
        {/* 1순위는 왜 추천하는지까지 이름표와 함께 보여 준다 */}
        {isBest && (ally.length > 0 || counter.length > 0) && (
          <span className='pick-reasons'>
            <Faces champions={ally} variant='ally' withLabel />
            <Faces champions={counter} variant='counter' withLabel />
          </span>
        )}
      </span>

      {/* 나머지는 자리가 좁아 초상화만 오른쪽에 붙인다. 테 색으로 아군·상대를 가른다. */}
      {!isBest && (
        <span className='pick-faces'>
          <Faces champions={ally} variant='ally' />
          <Faces champions={counter} variant='counter' />
        </span>
      )}
    </li>
  );
}

/** 지금 몇 번째 아이템을 살 차례인지 빗금 여섯 칸으로 보여 준다. */
function BuildProgress({ purchased }: { purchased: number }) {
  return (
    <ol className='progress' aria-label={`아이템 ${BUILD_SLOTS}칸 중 ${purchased}칸 구매`}>
      {Array.from({ length: BUILD_SLOTS }, (_, slot) => (
        <li
          key={slot}
          className={slot < purchased ? 'progress-done' : slot === purchased ? 'progress-next' : ''}
        />
      ))}
    </ol>
  );
}

/**
 * 창 높이를 내용 높이에 맞춘다. 추천이 다섯 개면 다섯 개가 다 보이게 창이 늘어난다.
 * 목록은 창이 최대 높이에 걸렸을 때만 스크롤되므로, 잘린 높이가 아니라 다 펼친 높이를 재서 알린다.
 */
function useFitWindowToContent(dependency: unknown) {
  const overlayRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const overlay = overlayRef.current;
    if (!overlay) return;

    const report = () => {
      const list = overlay.querySelector('.picks');
      const hidden = list ? list.scrollHeight - list.clientHeight : 0;
      window.windowControls.setOverlayHeight(overlay.offsetHeight + hidden);
    };

    report();
    // 글꼴이나 그림이 늦게 와서 높이가 달라지면 다시 알린다.
    const observer = new ResizeObserver(report);
    observer.observe(overlay);
    return () => observer.disconnect();
  }, [dependency]);

  return overlayRef;
}

function App() {
  const [items, setItems] = useState<RecommendedItem[] | null>(null);
  const [isInGame, setIsInGame] = useState(false);
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [purchasedCount, setPurchasedCount] = useState<number | null>(null);

  useEffect(() => {
    window.lcu.getState().then((state) => {
      setItems(state.recommendations);
      setIsInGame(state.phase === 'InProgress');
      setPurchasedCount(state.purchasedCount ?? null);
    });

    const unsubscribeItems = window.lcu.onItemsRecommendationChange(({ items, purchasedCount }) => {
      setItems(items);
      setPurchasedCount(purchasedCount);
    });

    const unsubscribePhase = window.lcu.onPhaseChange((phase) =>
      setIsInGame(phase === 'InProgress'),
    );

    return () => {
      unsubscribeItems();
      unsubscribePhase();
    };
  }, []);

  // 추천 목록이나 상태가 바뀌면 내용 높이도 바뀐다.
  const overlayRef = useFitWindowToContent(`${items?.length ?? 0}-${isInGame}-${isCollapsed}`);

  const toggleCollapsed = () => {
    const next = !isCollapsed;
    setIsCollapsed(next);
    window.windowControls.setCollapsed(next);
  };

  if (isCollapsed) {
    return (
      // 바깥 테두리는 잡고 끄는 자리, 가운데 로고는 누르는 자리다. 접힌 채로도 창을 옮길 수 있어야 한다.
      <div className='overlay-collapsed'>
        <button type='button' aria-label='오버레이 펼치기' onClick={toggleCollapsed}>
          <img src='./icon.png' alt='' />
        </button>
      </div>
    );
  }

  const hasItems = items !== null && items.length > 0;

  return (
    <div className='overlay' ref={overlayRef}>
      <header className='bar'>
        <span className='bar-title'>
          {purchasedCount === null ? '추천 아이템' : `${purchasedCount + 1}코어 추천`}
        </span>
        {purchasedCount !== null && <BuildProgress purchased={purchasedCount} />}

        <button
          type='button'
          className='bar-button'
          aria-label='오버레이 접기'
          onClick={toggleCollapsed}
        >
          <ChevronIcon />
        </button>
      </header>

      {hasItems ? (
        // 추천이 바뀌면 목록을 새로 그려 위에서부터 다시 차례로 나타나게 한다.
        <ol className='picks' key={items.map((item) => item.id).join()}>
          {items.map((item, index) => (
            <Pick key={item.id} item={item} rank={index + 1} />
          ))}
        </ol>
      ) : (
        <div className='overlay-empty'>
          {isInGame && <span className='overlay-loader' aria-hidden='true' />}
          <p>{isInGame ? '추천을 계산하는 중이에요' : '게임에 들어가면 추천이 시작돼요'}</p>
        </div>
      )}
    </div>
  );
}

export default App;
