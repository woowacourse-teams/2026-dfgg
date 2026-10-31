// 기술 소개: 개발자가 아니어도 "왜 믿을 만한지"가 읽히게. 한 줄에 이유 하나, 왼쪽은 글 오른쪽은 그림
import { motion } from 'motion/react';
import { type ReactNode, useRef } from 'react';

import useTrackInView from '../../hooks/useTrackInView';
import { strings } from '../../i18n/i18n';
import { championIcon, itemIcon } from '../../lib';
import SpeedDots from './SpeedDots';

const t = strings();
const text = t.features.tech;

const reveal = {
  initial: { opacity: 0, y: 20 },
  whileInView: { opacity: 1, y: 0 },
  viewport: { once: true, amount: 0.25 },
  transition: { duration: 0.7, ease: [0.16, 1, 0.3, 1] as const },
};

/** 모델이 순서를 매긴 예시. 상대에 회복이 많은 탱커 조합일 때 */
const RANKED_ITEMS = [3075, 3143, 6665];
/** 이유 예시: 가시 갑옷 - 아군 룰루와 잘 맞고, 상대 사일러스·잭스를 받아친다 */
const REASON_ITEM = 3075;
const REASON_SYNERGY = ['Lulu'];
const REASON_COUNTER = ['Sylas', 'Jax'];

const BEFORE_HOURS = 15;
const AFTER_HOURS = 2.5;

/**
 * 이유 한 줄. 번호 딱지 → 제목 → 짧게 나눈 설명 / 오른쪽에 그림.
 * 설명은 문구에 넣어 둔 줄바꿈을 넓은 화면에서만 따른다. 좁은 화면은 저절로 감긴다.
 */
function Story({
  index,
  title,
  body,
  children,
}: {
  index: number;
  title: string;
  body: string;
  children: ReactNode;
}) {
  return (
    <motion.article
      {...reveal}
      className='grid gap-8 border-t border-line py-14 md:grid-cols-[1fr_1.15fr] md:items-center md:gap-16 md:py-20'
    >
      <div>
        <span className='inline-block -skew-x-12 bg-accent-strong px-2 py-0.5 font-display text-sm font-bold text-white'>
          {String(index).padStart(2, '0')}
        </span>
        <h3 className='mt-4 text-[clamp(1.4rem,2.6vw,2rem)] leading-snug font-bold tracking-[-0.02em] text-white'>
          {title}
        </h3>
        <p className='mt-4 text-base leading-[1.8] text-ink-2 sm:whitespace-pre-line'>{body}</p>
      </div>
      <div>{children}</div>
    </motion.article>
  );
}

/** 빗금 막대. 데스크톱 앱 화면의 막대와 같은 기울기로 그린다 */
function Hatch({ percent, tone }: { percent: number; tone: 'accent' | 'muted' }) {
  return (
    <span className='relative block h-3 w-full -skew-x-[25deg] overflow-hidden'>
      <span className='absolute inset-0 bg-[repeating-linear-gradient(90deg,rgb(255_255_255/0.08)_0_5px,transparent_5px_8px)]' />
      <motion.span
        initial={{ scaleX: 0 }}
        whileInView={{ scaleX: 1 }}
        viewport={{ once: true }}
        transition={{ duration: 1, ease: [0.16, 1, 0.3, 1] }}
        style={{ width: `${percent}%` }}
        className={`absolute inset-y-0 left-0 origin-left ${
          tone === 'accent'
            ? 'bg-[repeating-linear-gradient(90deg,var(--color-accent)_0_5px,transparent_5px_8px)]'
            : 'bg-[repeating-linear-gradient(90deg,var(--color-ink-3)_0_5px,transparent_5px_8px)]'
        }`}
      />
    </span>
  );
}

/** 큰 숫자 둘. 반올림 안내는 숫자 바로 아래에 붙인다 */
function DataStats() {
  return (
    <div>
      <dl className='grid grid-cols-2 gap-6'>
        {text.data.stats.map((stat) => (
          <div key={stat.label}>
            <dd className='font-display text-[clamp(1.9rem,5vw,3.6rem)] leading-none font-bold whitespace-nowrap text-white'>
              {stat.value}
              <span className='ml-1 text-lg text-ink-2'>{stat.unit}</span>
            </dd>
            <dt className='mt-2 text-sm text-ink-3'>{stat.label}</dt>
          </div>
        ))}
      </dl>
      <p className='mt-4 text-xs text-ink-3'>{text.data.note}</p>
    </div>
  );
}

/** 네 갈래에서 후보를 모으고 → AI 모델이 순서를 매긴다 */
function ModelDiagram() {
  return (
    <div>
      <div className='grid items-center gap-4 sm:grid-cols-[1fr_auto_1fr]'>
        <figure>
          <figcaption className='text-xs text-ink-3'>{text.model.sourcesLabel}</figcaption>
          <ul className='mt-2 flex flex-col gap-1.5'>
            {text.model.sources.map((source) => (
              <li
                key={source}
                className='rounded-md bg-surface-2 px-3 py-2 text-sm text-ink-2 ring-1 ring-line'
              >
                {source}
              </li>
            ))}
          </ul>
        </figure>
        <span
          aria-hidden='true'
          className='rotate-90 justify-self-center font-display text-2xl text-accent sm:rotate-0'
        >
          →
        </span>
        <figure>
          <figcaption className='text-xs text-ink-3'>{text.model.rankedLabel}</figcaption>
          <ol className='mt-2 flex flex-col gap-1.5'>
            {RANKED_ITEMS.map((id, index) => (
              <li
                key={id}
                className={`flex items-center gap-3 rounded-md px-3 py-2 ring-1 ${
                  index === 0 ? 'bg-accent-strong/15 ring-accent/50' : 'bg-surface-2 ring-line'
                }`}
              >
                <b
                  className={`-skew-x-12 rounded-sm px-1.5 text-xs font-extrabold ${
                    index === 0 ? 'bg-accent-strong text-white' : 'bg-white/10 text-ink-2'
                  }`}
                >
                  {index + 1}
                </b>
                <img src={itemIcon(id)} alt='' width={28} height={28} className='size-7 rounded' />
                <span className='text-sm font-medium text-white'>{t.items[id]?.name}</span>
              </li>
            ))}
          </ol>
        </figure>
      </div>
      <p className='mt-4 text-xs leading-relaxed text-ink-3 sm:whitespace-pre-line'>
        {text.model.note}
      </p>
    </div>
  );
}

function Faces({ ids, tone }: { ids: string[]; tone: 'win' | 'loss' }) {
  return (
    <span className='flex -space-x-1.5'>
      {ids.map((id) => (
        <img
          key={id}
          src={championIcon(id)}
          alt={t.champions[id] ?? id}
          title={t.champions[id] ?? id}
          width={30}
          height={30}
          className={`size-7.5 rounded-full ring-2 ${tone === 'win' ? 'ring-win' : 'ring-loss'}`}
        />
      ))}
    </span>
  );
}

/** 오버레이의 추천 한 줄을 크게 떼어 와서 이유 부분을 보여 준다 */
function ReasonCard() {
  const item = t.items[REASON_ITEM];
  return (
    <div className='mx-auto max-w-100 rounded-2xl bg-overlay p-5 ring-1 ring-accent/40 shadow-[0_20px_60px_-25px_rgb(230_57_80/0.5)]'>
      <div className='flex items-center gap-3'>
        <img
          src={itemIcon(REASON_ITEM)}
          alt=''
          width={52}
          height={52}
          className='size-13 rounded-lg'
        />
        <div>
          <p className='text-lg font-bold text-white'>{item?.name}</p>
          <p className='text-xs text-ink-3'>{item?.traits.join(' · ')}</p>
        </div>
      </div>
      <div className='mt-5 flex flex-wrap items-center gap-x-6 gap-y-3 text-sm font-bold'>
        <span className='flex items-center gap-2 text-win'>
          {text.reason.synergy}
          <Faces ids={REASON_SYNERGY} tone='win' />
        </span>
        <span className='flex items-center gap-2 text-loss'>
          {text.reason.counter}
          <Faces ids={REASON_COUNTER} tone='loss' />
        </span>
      </div>
    </div>
  );
}

function PatchBars() {
  return (
    <figure>
      <figcaption className='text-xs text-ink-3'>{text.patch.caption}</figcaption>
      <dl className='mt-3 flex flex-col gap-5'>
        <div>
          <div className='flex items-baseline justify-between text-sm'>
            <dt className='text-ink-3'>{text.patch.before}</dt>
            <dd className='font-display text-lg text-ink-2'>{text.patch.beforeValue}</dd>
          </div>
          <Hatch percent={100} tone='muted' />
        </div>
        <div>
          <div className='flex items-baseline justify-between text-sm'>
            <dt className='font-bold text-white'>{text.patch.after}</dt>
            <dd className='font-display text-2xl font-bold text-white'>{text.patch.afterValue}</dd>
          </div>
          <Hatch percent={(AFTER_HOURS / BEFORE_HOURS) * 100} tone='accent' />
        </div>
      </dl>
    </figure>
  );
}

export default function Tech() {
  const ref = useRef<HTMLElement>(null);
  useTrackInView(ref, 'features-tech-view');

  return (
    <section
      ref={ref}
      id='tech'
      aria-labelledby='tech-title'
      className='mx-auto max-w-300 scroll-mt-24 px-4 sm:px-8'
    >
      <motion.header {...reveal} className='pb-14 md:pb-20'>
        <p className='text-sm font-bold text-accent'>{text.kicker}</p>
        {/* 히어로 글꼴은 히어로 제목 글자만 남긴 파일이라 여기서는 본문 글꼴을 굵게 쓴다 */}
        <h2
          id='tech-title'
          className='mt-3 text-[clamp(2.2rem,6vw,4.2rem)] leading-[1.08] font-extrabold tracking-[-0.03em] text-white'
        >
          {text.title.map((line) => (
            <span key={line} className='block'>
              {line}
            </span>
          ))}
        </h2>
        <p className='mt-6 text-base leading-[1.8] text-ink-2 sm:text-lg sm:whitespace-pre-line'>
          {text.intro}
        </p>
      </motion.header>

      <Story index={1} title={text.data.title} body={text.data.body}>
        <DataStats />
      </Story>
      <Story index={2} title={text.model.title} body={text.model.body}>
        <ModelDiagram />
      </Story>
      <Story index={3} title={text.reason.title} body={text.reason.body}>
        <ReasonCard />
      </Story>
      <Story index={4} title={text.patch.title} body={text.patch.body}>
        <PatchBars />
      </Story>
      <Story index={5} title={text.speed.title} body={text.speed.body}>
        <SpeedDots />
        <p className='mt-3 text-xs text-ink-3'>{text.speed.caption}</p>
      </Story>
    </section>
  );
}
