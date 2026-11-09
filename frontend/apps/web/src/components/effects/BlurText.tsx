// 단어별 흐림 등장 텍스트 파일 (React Bits BlurText 기반 · MIT + Commons Clause © David Haz)
import { motion, type Transition } from 'motion/react';
import { useEffect, useRef, useState } from 'react';

interface BlurTextProps {
  lines: string[];
  delay?: number;
  className?: string;
  stepDuration?: number;
}

const FROM = { filter: 'blur(10px)', opacity: 0, y: -40 };
const KEYFRAMES = {
  filter: ['blur(10px)', 'blur(5px)', 'blur(0px)'],
  opacity: [0, 0.5, 1],
  y: [-40, 5, 0],
};

export default function BlurText({
  lines,
  delay = 90,
  className = '',
  stepDuration = 0.35,
}: BlurTextProps) {
  const ref = useRef<HTMLSpanElement>(null);
  const [isInView, setIsInView] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) {
        setIsInView(true);
        observer.disconnect();
      }
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  let wordIndex = 0;

  return (
    <span ref={ref} className={className}>
      {lines.map((line) => (
        <span key={line} className='block'>
          {line.split(' ').map((word, i, words) => {
            const transition: Transition = {
              duration: stepDuration * 2,
              times: [0, 0.5, 1],
              delay: (wordIndex++ * delay) / 1000,
            };
            return (
              <motion.span
                key={`${word}-${i}`}
                initial={FROM}
                animate={isInView ? KEYFRAMES : FROM}
                transition={transition}
                className='inline-block will-change-[transform,filter,opacity]'
              >
                {word}
                {i < words.length - 1 && ' '}
              </motion.span>
            );
          })}
        </span>
      ))}
    </span>
  );
}
