import { type KeyboardEvent, useEffect, useId, useMemo, useRef, useState } from 'react';

import { type ChampionInfo, isChosungOnly, useDebounced } from '../hooks/useChampions';

const DEBOUNCE_MS = 120;
const MAX_SUGGESTIONS = 7;

interface ChampionComboboxProps {
  value: string;
  champions: ChampionInfo[];
  label: string;
  accentClass: string;
  /** 내 포지션 칸. 값이 비어 있어도 테두리를 계속 강조한다. */
  highlighted?: boolean;
  onChange: (name: string) => void;
}

export default function ChampionCombobox({
  value,
  champions,
  label,
  accentClass,
  highlighted = false,
  onChange,
}: ChampionComboboxProps) {
  const [query, setQuery] = useState(value);
  const [isOpen, setIsOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const listboxId = useId();
  const rootRef = useRef<HTMLDivElement>(null);

  const debouncedQuery = useDebounced(query, DEBOUNCE_MS);

  // 부모가 값을 바꾸면(초기화 등) 입력창도 따라간다. 렌더 중 동기화가 effect보다 싸다.
  const [syncedValue, setSyncedValue] = useState(value);
  if (syncedValue !== value) {
    setSyncedValue(value);
    setQuery(value);
  }

  const suggestions = useMemo(() => {
    const keyword = debouncedQuery.trim().toLowerCase();
    if (!keyword) return [];

    // "ㅇㄹ"처럼 초성만 쳤으면 초성끼리 비교한다.
    const byChosung = isChosungOnly(keyword);
    const target = (champion: ChampionInfo) => (byChosung ? champion.chosung : champion.searchKey);

    // 앞에서부터 일치하는 챔피언을 위로 올린다 — 밴픽창에선 첫 글자 입력이 대부분이다.
    return champions
      .filter((champion) => target(champion).includes(keyword))
      .sort((a, b) => {
        const aStarts = target(a).startsWith(keyword) ? 0 : 1;
        const bStarts = target(b).startsWith(keyword) ? 0 : 1;
        return aStarts - bStarts;
      })
      .slice(0, MAX_SUGGESTIONS);
  }, [champions, debouncedQuery]);

  const isExactMatch = champions.some((champion) => champion.name === value);
  const showList = isOpen && suggestions.length > 0;

  // 검색어가 바뀌면 하이라이트를 첫 항목으로 되돌린다.
  const [syncedQuery, setSyncedQuery] = useState(debouncedQuery);
  if (syncedQuery !== debouncedQuery) {
    setSyncedQuery(debouncedQuery);
    setActiveIndex(0);
  }

  // 바깥을 누르면 닫는다.
  useEffect(() => {
    if (!isOpen) return;
    const handlePointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setIsOpen(false);
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, [isOpen]);

  const select = (champion: ChampionInfo) => {
    onChange(champion.name);
    setQuery(champion.name);
    setIsOpen(false);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Escape') {
      setIsOpen(false);
      return;
    }
    if (!showList) return;

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      setActiveIndex((index) => (index + step + suggestions.length) % suggestions.length);
      return;
    }
    // Enter/Tab 둘 다 확정 — 탭으로 다음 칸까지 한 번에 넘어가야 빠르다.
    if (event.key === 'Enter' || event.key === 'Tab') {
      if (event.key === 'Enter') event.preventDefault();
      select(suggestions[activeIndex]);
    }
  };

  const activeChampion = champions.find((champion) => champion.name === value);

  return (
    <div ref={rootRef} className="relative">
      <div
        className={`chamfer-sm flex items-center gap-2 bg-surface-2 pl-2 shadow-[inset_0_0_0_1px_var(--color-line)] transition-shadow focus-within:shadow-[inset_0_0_0_2px_var(--color-hextech)] ${
          isExactMatch || highlighted ? accentClass : ''
        }`}
      >
        {activeChampion ? (
          <img
            src={activeChampion.imageUrl}
            alt=""
            width={32}
            height={32}
            className="shrink-0 rounded-sm"
          />
        ) : (
          <span
            aria-hidden="true"
            className="size-8 shrink-0 rounded-sm bg-surface shadow-[inset_0_0_0_1px_var(--color-line)]"
          />
        )}
        <input
          type="text"
          role="combobox"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            onChange(event.target.value);
            setIsOpen(true);
          }}
          onFocus={() => setIsOpen(true)}
          onKeyDown={handleKeyDown}
          placeholder={label}
          aria-label={label}
          aria-expanded={showList}
          aria-controls={showList ? listboxId : undefined}
          aria-activedescendant={showList ? `${listboxId}-${activeIndex}` : undefined}
          aria-autocomplete="list"
          autoComplete="off"
          className="min-w-0 flex-1 bg-transparent py-2.5 pr-3 text-ink outline-none placeholder:text-ink-3"
        />
      </div>

      {showList && (
        <ul
          id={listboxId}
          role="listbox"
          aria-label={`${label} 추천`}
          className="chamfer-sm absolute top-full right-0 left-0 z-20 mt-1 overflow-hidden bg-surface shadow-[inset_0_0_0_1px_var(--color-hextech),0_12px_28px_rgb(0_0_0/0.6)]"
        >
          {suggestions.map((champion, index) => (
            <li
              key={champion.id}
              id={`${listboxId}-${index}`}
              role="option"
              aria-selected={index === activeIndex}
              onPointerDown={(event) => {
                event.preventDefault();
                select(champion);
              }}
              onPointerEnter={() => setActiveIndex(index)}
              className={`flex cursor-pointer items-center gap-2.5 px-2 py-1.5 transition-colors ${
                index === activeIndex ? 'bg-surface-2 text-hextech' : 'text-ink-2'
              }`}
            >
              <img
                src={champion.imageUrl}
                alt=""
                width={28}
                height={28}
                loading="lazy"
                className="shrink-0 rounded-sm"
              />
              <span className="truncate text-sm">{champion.name}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
