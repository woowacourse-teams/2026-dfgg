import './style.css';

import { useEffect, useLayoutEffect, useRef, useState } from 'react';

import type {
  NamedEntry,
  OverlayClickTarget,
  RecommendedItem,
  SituationalItem,
} from '../../shared/types';
import { t } from '../i18n';
import { ChevronIcon } from '../icons';

type Variant = 'ally' | 'counter';

const VARIANT_LABEL: Record<Variant, string> = {
  ally: t.overlay.ally,
  counter: t.overlay.counter,
};

// 초상화에 붙는 설명. 챔피언 이름과 관계를 한 줄에 담는다.
const VARIANT_RELATION: Record<Variant, string> = {
  ally: t.overlay.allyRelation,
  counter: t.overlay.counterRelation,
};

/** 순위 숫자를 붙이는 개수. 그 아래는 자리로만 순서를 알린다. */
const TOP_COUNT = 3;
/** 한 판에 채우는 아이템 칸 수 */
const BUILD_SLOTS = 6;
/**
 * 오버레이에 보이는 줄 수. 코어와 상황템을 합쳐 이 수를 맞춘다.
 * 상황템이 없으면 코어 5개, 1개면 코어 4개, 2개(최대)면 코어 3개.
 */
const MAX_ROWS = 5;

const formatGold = (gold: number) => `${gold.toLocaleString('ko-KR')}G`;

/** 코어 하나를 향해 지금 살 하위 아이템. 구매 순서대로 › 로 잇고 끝에 드는 골드를 붙인다. */
function Bundle({ item, size }: { item: RecommendedItem; size: 'large' | 'small' }) {
  const bundle = item.purchaseBundle;
  if (!bundle) return null;

  // 사는 순서대로 늘어놓는다. 순서에 없는 재료는 뒤에 붙여 빠뜨리지 않는다.
  const ordered = [
    ...bundle.purchaseOrder.flatMap((id) => bundle.items.filter((entry) => entry.itemId === id)),
    ...bundle.items.filter((entry) => !bundle.purchaseOrder.includes(entry.itemId)),
  ];

  return (
    <span className={`bundle bundle-${size}`}>
      {ordered.map((entry, index) => (
        <span key={entry.itemId} className='bundle-step'>
          {index > 0 && <i aria-hidden='true'>›</i>}
          <img src={entry.imageUrl} alt={entry.name} title={entry.name} />
          {entry.quantity > 1 && <b>×{entry.quantity}</b>}
        </span>
      ))}
      <span className='cost'>{formatGold(bundle.additionalCost)}</span>
    </span>
  );
}

/** 상대 조합 때문에 고려할 아이템 한 줄. 지금 못 사면 흐리게 두고 모자란 골드를 적는다. */
function SituationalRow({ item, gold }: { item: SituationalItem; gold: number | null }) {
  const shortage = gold === null ? null : item.additionalCost - gold;

  return (
    <li data-click='situational' className={`situ ${item.affordable ? '' : 'situ-locked'}`}>
      <img className='situ-art' src={item.imageUrl} alt='' />
      <span className='pick-body'>
        <span className='pick-name'>{item.name}</span>
        {item.description.traits.length > 0 && (
          <span className='pick-traits'>{item.description.traits.join(' · ')}</span>
        )}
      </span>
      <Faces champions={item.description.counter} variant='counter' />
      <span className={`cost ${item.affordable ? '' : 'cost-short'}`}>
        {item.affordable || shortage === null || shortage <= 0
          ? formatGold(item.additionalCost)
          : `${formatGold(shortage)} 부족`}
      </span>
    </li>
  );
}

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

  const art = (
    <span className='pick-art'>
      <img src={item.imageUrl} alt='' />
      {rank <= TOP_COUNT && (
        <b className='pick-rank' aria-label={t.overlay.rank(rank)}>
          {rank}
        </b>
      )}
    </span>
  );

  if (isBest) {
    return (
      <li
        data-click='item'
        className='pick pick-best'
        style={{ '--order': 0 } as React.CSSProperties}
      >
        <span className='pick-main'>
          {art}
          <span className='pick-body'>
            <span className='pick-name'>{item.name}</span>
            {traits.length > 0 && (
              <span className='pick-traits' title={traits.join(' · ')}>
                {traits.join(' · ')}
              </span>
            )}
            {/* 1순위는 왜 추천하는지까지 이름표와 함께 보여 준다 */}
            {(ally.length > 0 || counter.length > 0) && (
              <span className='pick-reasons'>
                <Faces champions={ally} variant='ally' withLabel />
                <Faces champions={counter} variant='counter' withLabel />
              </span>
            )}
          </span>
        </span>

        {/* 이 코어를 향해 지금 상점에서 살 것 */}
        {item.purchaseBundle && (
          <span className='now-buy'>
            <span className='now-buy-label'>지금 살 것</span>
            <Bundle item={item} size='large' />
          </span>
        )}
      </li>
    );
  }

  return (
    <li
      data-click='item'
      className={`pick ${rank > TOP_COUNT ? 'pick-rest' : ''}`}
      style={{ '--order': rank - 1 } as React.CSSProperties}
    >
      {art}

      <span className='pick-body'>
        <span className='pick-name'>{item.name}</span>
        {/* 지금 살 재료가 있으면 그걸, 없으면 아이템 특징을 보여 준다 */}
        {item.purchaseBundle ? (
          <Bundle item={item} size='small' />
        ) : (
          traits.length > 0 && (
            <span className='pick-traits' title={traits.join(' · ')}>
              {traits.join(' · ')}
            </span>
          )
        )}
      </span>

      {/* 자리가 좁아 초상화만 오른쪽에 붙인다. 테 색으로 아군·상대를 가른다. */}
      <span className='pick-faces'>
        <Faces champions={ally} variant='ally' />
        <Faces champions={counter} variant='counter' />
      </span>
    </li>
  );
}

/** 지금 몇 번째 아이템을 살 차례인지 빗금 여섯 칸으로 보여 준다. */
function BuildProgress({ purchased }: { purchased: number }) {
  return (
    <ol className='progress' aria-label={t.overlay.progress(BUILD_SLOTS, purchased)}>
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
  const [situationalItems, setSituationalItems] = useState<SituationalItem[]>([]);
  const [availableGold, setAvailableGold] = useState<number | null>(null);

  useEffect(() => {
    window.lcu.getState().then((state) => {
      setItems(state.recommendations);
      setIsInGame(state.phase === 'InProgress');
      setPurchasedCount(state.purchasedCount ?? null);
      setSituationalItems(state.situationalItems ?? []);
      setAvailableGold(state.availableGold ?? null);
    });

    const unsubscribeItems = window.lcu.onItemsRecommendationChange((update) => {
      setItems(update.items);
      setPurchasedCount(update.purchasedCount);
      setSituationalItems(update.situationalItems);
      setAvailableGold(update.availableGold);
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
  const overlayRef = useFitWindowToContent(
    `${items?.length ?? 0}-${situationalItems.length}-${isInGame}-${isCollapsed}`,
  );

  const toggleCollapsed = () => {
    const next = !isCollapsed;
    setIsCollapsed(next);
    window.windowControls.setCollapsed(next);
  };

  const trackClick = (e: React.PointerEvent) => {
    const el = (e.target as HTMLElement).closest<HTMLElement>('[data-click]');
    window.analytics.overlayClick((el?.dataset.click as OverlayClickTarget) ?? 'other');
  };

  if (isCollapsed) {
    return (
      // 바깥 테두리는 잡고 끄는 자리, 가운데 로고는 누르는 자리다. 접힌 채로도 창을 옮길 수 있어야 한다.
      <div className='overlay-collapsed' onPointerDown={trackClick}>
        <button type='button' aria-label={t.overlay.expand} onClick={toggleCollapsed}>
          <img src='./icon.png' alt='' />
        </button>
      </div>
    );
  }

  const hasItems = items !== null && items.length > 0;
  // 상황템이 들어오면 그만큼 아래쪽 코어를 덜 보여 줘서 전체 줄 수를 맞춘다.
  const situational = situationalItems.slice(0, MAX_ROWS - 1);
  const cores = items?.slice(0, MAX_ROWS - situational.length) ?? [];

  return (
    <div className='overlay' ref={overlayRef} onPointerDown={trackClick}>
      <header className='bar'>
        <span className='bar-title'>
          {purchasedCount === null ? t.overlay.title : t.overlay.coreTitle(purchasedCount + 1)}
        </span>
        {purchasedCount !== null && <BuildProgress purchased={purchasedCount} />}
        {availableGold !== null && (
          <span className='bar-gold' title='보유 골드'>
            {formatGold(availableGold)}
          </span>
        )}

        <button
          type='button'
          className='bar-button'
          aria-label={t.overlay.collapse}
          onClick={toggleCollapsed}
          data-click='collapse'
        >
          <ChevronIcon />
        </button>
      </header>

      {hasItems ? (
        // 추천이 바뀌면 목록을 새로 그려 위에서부터 다시 차례로 나타나게 한다.
        <>
          <ol className='picks' key={cores.map((item) => item.id).join()}>
            {cores.map((item, index) => (
              <Pick key={item.id} item={item} rank={index + 1} />
            ))}
          </ol>

          {situational.length > 0 && (
            <section className='situ-section' aria-label='상대 조합 대응'>
              <h3>상대 조합 대응</h3>
              <ul>
                {situational.map((item) => (
                  <SituationalRow key={item.id} item={item} gold={availableGold} />
                ))}
              </ul>
            </section>
          )}
        </>
      ) : (
        <div className='overlay-empty' data-click='empty'>
          {isInGame && <span className='overlay-loader' aria-hidden='true' />}
          <p>{isInGame ? t.overlay.calculating : t.overlay.waiting}</p>
        </div>
      )}
    </div>
  );
}

export default App;
