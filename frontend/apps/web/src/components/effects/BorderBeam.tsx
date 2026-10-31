// 테두리를 따라 도는 빛 파일 (Magic UI BorderBeam 기반 · MIT © Magic UI)
import type { CSSProperties } from 'react';

interface BorderBeamProps {
  size?: number;
  duration?: number;
  colorFrom?: string;
  colorTo?: string;
  borderWidth?: number;
}

export default function BorderBeam({
  size = 80,
  duration = 6,
  colorFrom = '#f0c060',
  colorTo = '#e63950',
  borderWidth = 1.5,
}: BorderBeamProps) {
  return (
    <div
      aria-hidden='true'
      className='pointer-events-none absolute inset-0 rounded-[inherit] border-(length:--border-beam-width) border-transparent mask-[linear-gradient(transparent,transparent),linear-gradient(#000,#000)] mask-intersect [mask-clip:padding-box,border-box]'
      style={{ '--border-beam-width': `${borderWidth}px` } as CSSProperties}
    >
      {/* 끝없이 도는 장식이라 JS 대신 CSS 애니메이션으로 돌린다 */}
      <div
        className='absolute aspect-square animate-[border-beam_var(--beam-duration)_linear_infinite] bg-linear-to-l from-(--color-from) via-(--color-to) to-transparent motion-reduce:animate-none'
        style={
          {
            width: size,
            offsetPath: `rect(0 auto auto 0 round ${size}px)`,
            offsetDistance: '0%',
            '--color-from': colorFrom,
            '--color-to': colorTo,
            '--beam-duration': `${duration}s`,
          } as CSSProperties
        }
      />
    </div>
  );
}
