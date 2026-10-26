import { useState } from 'react';

import { Hatch } from '../home/components/MatchSection/parts';
import { type CounterEnemy, COUNTERS, POSITION_LABEL, rateAt } from './data';
import { championIcon, championSplash, championTile } from './icons';

const MY = COUNTERS.myPosition;
const MY_LABEL = POSITION_LABEL[MY];

/** 이 비율 이상이면 내 라인으로 온다고 본다. 아래면 다른 라인 챔피언. */
const LANE_LIKELY = 50;
const MAX_ENEMIES = 5;

/** 가장 많이 가는 라인 하나. 라인 집계가 없으면 undefined */
const mainLane = (target: CounterEnemy) => target.positions[0];

function Head({ note }: { note?: string }) {
  return (
    <div className='section-head'>
      <h1 className='section-title'>카운터 추천</h1>
      {note && <span className='ct-meta'>{note}</span>}
    </div>
  );
}

/** 맞라인 딱지. 라인 집계가 없으면 무슨 라인인지 모른다고 적는다. */
function laneTag(target: CounterEnemy): { text: string; likely: boolean } {
  const main = mainLane(target);
  if (!main) return { text: '라인 정보 없음', likely: false };
  if (rateAt(target, MY) >= LANE_LIKELY) return { text: '맞라인 유력', likely: true };
  return { text: `${POSITION_LABEL[main.position]} 챔피언`, likely: false };
}

/** 고른 상대 한 명. 전적 카드처럼 그림을 사선으로 걸고, 내 라인으로 올 확률을 빗금으로. */
function EnemyCard({ target, hasPositions }: { target: CounterEnemy; hasPositions: boolean }) {
  const rate = rateAt(target, MY);
  const tag = laneTag(target);

  return (
    <article className={`ctb-card ${tag.likely ? '' : 'ctb-card-off'}`}>
      <span className='ctb-art'>
        <img src={championSplash(target.championId)} alt='' />
      </span>
      <div className='ctb-body'>
        <span className={`ctb-tag ${tag.likely ? '' : 'ctb-tag-off'}`}>{tag.text}</span>
        <strong>{target.name}</strong>
        {hasPositions && (
          <span className='ctb-rate'>
            <small>
              {MY_LABEL}로 올 확률 <b>{rate.toFixed(1)}%</b>
            </small>
            <span className='ctb-rate-bar'>
              <Hatch percent={rate} from='left' />
            </span>
          </span>
        )}
      </div>
    </article>
  );
}

/** 받아칠 챔피언. 번호 순서가 곧 강한 순. 다섯이 안 되면 있는 만큼만 같은 크기로 둔다. */
function CounterTiles({ target }: { target: CounterEnemy }) {
  // 챔피언 이름마다 받침이 달라 조사를 붙이지 않는다
  if (!target.counters.length) {
    const main = mainLane(target);
    return (
      <p className='ct-empty'>
        {main
          ? `${target.name} · 주로 ${POSITION_LABEL[main.position]} 챔피언이라 ${MY_LABEL} 카운터 정보가 없어요`
          : `${target.name} ${MY_LABEL} 카운터 정보가 아직 없어요`}
      </p>
    );
  }

  return (
    <ol className='ctb-tiles'>
      {target.counters.map((counter, at) => (
        <li key={counter.championId}>
          <img src={championTile(counter.championId)} alt='' />
          <i>{at + 1}</i>
          <span>{counter.name}</span>
        </li>
      ))}
    </ol>
  );
}

type Props = {
  enemies: CounterEnemy[];
  /** 라인 분포 기준 패치. 집계가 없으면 null */
  patch: string | null;
  /** 내 챔피언을 확정했는지. 확정하면 카운터는 더 쓸 데가 없어 맞라인 한 줄로 접는다. */
  locked?: boolean;
};

/** 최종(B안): 맞라인으로 올 상대 한 명을 크게, 받아칠 챔피언을 큰 타일로. 다른 상대는 알약 탭. */
export function Counter({ enemies, patch, locked = false }: Props) {
  const [index, setIndex] = useState(0);
  // 내 라인으로 올 확률이 높은 순. 맨 앞이 맞라인 유력
  const byLane = [...enemies].sort((a, b) => rateAt(b, MY) - rateAt(a, MY));
  const target = byLane[Math.min(index, byLane.length - 1)];

  // 블루 1픽: 상대가 아직 아무도 공개하지 않았다
  if (!target) {
    return (
      <section className='ct' aria-label='카운터 추천'>
        <Head />
        <p className='ct-empty ct-wait'>
          <b>상대 픽을 기다리는 중</b>
          <small>상대가 챔피언을 공개할 때마다 받아칠 챔피언을 알려 줘요</small>
        </p>
      </section>
    );
  }

  const hasPositions = patch !== null && target.positions.length > 0;

  if (locked) {
    const tag = laneTag(target);
    return (
      <section className='ct' aria-label='맞라인'>
        <div className='ct-locked'>
          <img src={championIcon(target.championId)} alt='' />
          <span className={`ctb-tag ${tag.likely ? '' : 'ctb-tag-off'}`}>{tag.text}</span>
          <b>{target.name}</b>
          {hasPositions && (
            <small>
              {MY_LABEL} {rateAt(target, MY).toFixed(1)}%
            </small>
          )}
          <span className='ct-locked-note'>
            상대 {enemies.length}/{MAX_ENEMIES}
          </span>
        </div>
      </section>
    );
  }

  return (
    <section className='ct' aria-label='카운터 추천'>
      <Head note={`상대 공개 ${enemies.length}/${MAX_ENEMIES}${patch ? ` · 패치 ${patch}` : ''}`} />
      <EnemyCard target={target} hasPositions={hasPositions} />
      <CounterTiles target={target} />

      {/* 상대가 둘 이상일 때만 바꿔 볼 탭을 둔다 */}
      {byLane.length > 1 && (
        <div className='board-views ctb-switch' role='tablist' aria-label='상대 챔피언'>
          {byLane.map((other, at) => (
            <button
              key={other.championId}
              type='button'
              role='tab'
              aria-selected={other === target}
              onClick={() => setIndex(at)}
            >
              <img src={championIcon(other.championId)} alt='' />
              {other.name}
              {other.positions.length > 0 && <small>{Math.round(rateAt(other, MY))}%</small>}
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

/* ---------- 상황별 데이터 ---------- */
const [ARI, ZED, VIEGO] = COUNTERS.enemies;

export const COUNTER_CASES = {
  blueFirst: [] as CounterEnemy[],
  one: [ARI],
  many: COUNTERS.enemies,
  noLaner: [VIEGO],
  // 아군·적·밴에 들어간 카운터는 빠지고 다음 순위로 채운다. 채울 게 모자라면 다섯이 안 된다
  fewCounters: [{ ...ARI, counters: ARI.counters.slice(0, 3) }],
  // 이번 패치 새 챔피언처럼 라인 집계가 없을 때
  noPositions: [{ ...ZED, championId: 800, id: 800, name: '멜', positions: [] }],
};
