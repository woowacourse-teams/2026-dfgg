// 위에서 쏟아지는 빛줄기 배경 파일 (Magic UI LightRays 기반 · MIT © Magic UI)
import { motion } from 'motion/react';
import { type CSSProperties, useState } from 'react';

interface LightRaysProps {
  count?: number;
  color?: string;
  blur?: number;
  speed?: number;
  length?: string;
}

interface Ray {
  id: string;
  left: number;
  rotate: number;
  width: number;
  swing: number;
  delay: number;
  duration: number;
  intensity: number;
}

const createRays = (count: number, cycle: number): Ray[] =>
  Array.from({ length: count }, (_, index) => {
    const left = 8 + Math.random() * 84;
    return {
      id: `${index}-${Math.round(left * 10)}`,
      left,
      rotate: -28 + Math.random() * 56,
      width: 160 + Math.random() * 160,
      swing: 0.8 + Math.random() * 1.8,
      delay: Math.random() * cycle,
      duration: cycle * (0.75 + Math.random() * 0.5),
      intensity: 0.6 + Math.random() * 0.5,
    };
  });

export default function LightRays({
  count = 7,
  color = 'rgba(230, 57, 80, 0.35)',
  blur = 36,
  speed = 14,
  length = '80vh',
}: LightRaysProps) {
  const [rays] = useState(() => createRays(count, Math.max(speed, 0.1)));

  return (
    <div
      aria-hidden='true'
      className='pointer-events-none absolute inset-0 isolate overflow-hidden mask-b-from-50% mask-b-to-100%'
      style={
        {
          '--light-rays-color': color,
          '--light-rays-blur': `${blur}px`,
          '--light-rays-length': length,
        } as CSSProperties
      }
    >
      <div className='absolute inset-0 bg-[radial-gradient(circle_at_20%_15%,color-mix(in_srgb,var(--light-rays-color)_45%,transparent),transparent_70%)] opacity-60' />
      <div className='absolute inset-0 bg-[radial-gradient(circle_at_80%_10%,color-mix(in_srgb,var(--light-rays-color)_35%,transparent),transparent_75%)] opacity-60' />
      {rays.map((ray) => (
        <motion.div
          key={ray.id}
          className='absolute -top-[12%] left-(--ray-left) h-(--light-rays-length) w-(--ray-width) origin-top -translate-x-1/2 rounded-full bg-linear-to-b from-[color-mix(in_srgb,var(--light-rays-color)_70%,transparent)] to-transparent opacity-0 mix-blend-screen blur-(--light-rays-blur)'
          style={{ '--ray-left': `${ray.left}%`, '--ray-width': `${ray.width}px` } as CSSProperties}
          initial={{ rotate: ray.rotate }}
          animate={{
            opacity: [0, ray.intensity, 0],
            rotate: [ray.rotate - ray.swing, ray.rotate + ray.swing, ray.rotate - ray.swing],
          }}
          transition={{
            duration: ray.duration,
            repeat: Infinity,
            ease: 'easeInOut',
            delay: ray.delay,
            repeatDelay: ray.duration * 0.1,
          }}
        />
      ))}
    </div>
  );
}
