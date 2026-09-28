// 섹션 노출 이벤트 훅 파일
import { type RefObject, useEffect } from 'react';

import { trackOnce } from '../lib';

const VISIBLE_RATIO = 0.3;

export default function useTrackInView(ref: RefObject<HTMLElement | null>, event: string) {
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          trackOnce(event);
          observer.disconnect();
        }
      },
      { threshold: VISIBLE_RATIO },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [ref, event]);
}
