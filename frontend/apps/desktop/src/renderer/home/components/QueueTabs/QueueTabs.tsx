import './QueueTabs.css';

import type { QueueFilter } from '../../lib/queues';

type Tab = { key: QueueFilter; label: string; count: number };

type Props = {
  tabs: Tab[];
  active: QueueFilter;
  onChange: (filter: QueueFilter) => void;
};

/** 가로 메뉴. 칸 너비가 같아 고른 칸의 순번만 알면 밑줄이 어디로 갈지 정해진다. */
function QueueTabs({ tabs, active, onChange }: Props) {
  const activeIndex = Math.max(
    0,
    tabs.findIndex((tab) => tab.key === active),
  );

  return (
    <div
      className='queue-tabs'
      role='tablist'
      aria-label='게임 종류'
      style={{ '--tab-count': tabs.length, '--tab-active': activeIndex } as React.CSSProperties}
    >
      <span className='queue-tabs-thumb' aria-hidden='true' />

      {tabs.map((tab) => (
        <button
          key={tab.key}
          type='button'
          role='tab'
          className='queue-tab'
          aria-selected={tab.key === active}
          // 판이 없는 종류는 눌러도 보여 줄 게 없다
          disabled={tab.count === 0}
          onClick={() => onChange(tab.key)}
        >
          {tab.label}
          <span className='queue-tab-count'>{tab.count}</span>
        </button>
      ))}
    </div>
  );
}

export default QueueTabs;
