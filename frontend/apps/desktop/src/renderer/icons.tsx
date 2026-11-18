// 글꼴 아이콘(Segoe Fluent Icons)은 윈도우에만 있어 다른 환경에서 네모로 깨진다. SVG 로 그린다.
const ICON_PROPS = {
  width: 12,
  height: 12,
  viewBox: '0 0 12 12',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const;

export function MinimizeIcon() {
  return (
    <svg {...ICON_PROPS}>
      <path d='M2.5 6h7' />
    </svg>
  );
}

export function CloseIcon() {
  return (
    <svg {...ICON_PROPS}>
      <path d='M3 3l6 6M9 3l-6 6' />
    </svg>
  );
}

export function ChevronIcon() {
  return (
    <svg {...ICON_PROPS}>
      <path d='M3 4.5l3 3 3-3' />
    </svg>
  );
}

export function ClockIcon() {
  return (
    <svg {...ICON_PROPS}>
      <circle cx='6' cy='6' r='4.3' />
      <path d='M6 3.6V6l1.6 1' />
    </svg>
  );
}

export function StarIcon() {
  return (
    <svg {...ICON_PROPS} fill='currentColor' stroke='none'>
      <path d='M6 .9l1.5 3.2 3.5.4-2.6 2.4.7 3.5L6 8.7 2.9 10.4l.7-3.5L1 4.5l3.5-.4z' />
    </svg>
  );
}
