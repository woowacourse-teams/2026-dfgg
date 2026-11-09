import './CounterSection.css';

import { useState } from 'react';

import type { CounterEnemy, Counters, Position } from '../../../../shared/types';
import { t } from '../../../i18n';
import { championIconUrl, championSplashUrl, championTileUrl } from '../../lib/ddragon';
import { Hatch } from '../MatchSection/parts';

/** 이 비율 이상이면 내 라인으로 온다고 본다. 아래면 다른 라인 챔피언. */
const LANE_LIKELY = 50;
const MAX_ENEMIES = 5;

const laneName = (position: Position) => t.champSelect.positions[position];

/** 내 포지션으로 올 확률. 상대 픽 중 누가 맞라인일지 가늠한다. */
const rateAt = (target: CounterEnemy, position: Position): number =>
  target.positions.find((entry) => entry.position === position)?.rate ?? 0;

/** 맞라인 딱지. 라인 집계가 없으면 무슨 라인인지 모른다고 적는다. */
function laneTag(target: CounterEnemy, my: Position): { text: string; likely: boolean } {
  const main = target.positions[0];
  if (!main) return { text: t.champSelect.noLaneData, likely: false };
  if (rateAt(target, my) >= LANE_LIKELY) return { text: t.champSelect.likelyLaner, likely: true };
  return { text: t.champSelect.laneChampion(laneName(main.position)), likely: false };
}

function Head({ note }: { note?: string }) {
  return (
    <div className='section-head'>
      <h1 className='section-title'>{t.champSelect.counterTitle}</h1>
      {note && <span className='ct-meta'>{note}</span>}
    </div>
  );
}

/** 고른 상대 한 명. 전적 카드처럼 그림을 사선으로 걸고, 내 라인으로 올 확률을 빗금으로. */
function EnemyCard({
  target,
  my,
  hasLanes,
}: {
  target: CounterEnemy;
  my: Position;
  hasLanes: boolean;
}) {
  const rate = rateAt(target, my);
  const tag = laneTag(target, my);

  return (
    <article className={`ctb-card ${tag.likely ? '' : 'ctb-card-off'}`}>
      <span className='ctb-art'>
        <img src={championSplashUrl(target.championId)} alt='' />
      </span>
      <div className='ctb-body'>
        <span className={`ctb-tag ${tag.likely ? '' : 'ctb-tag-off'}`}>{tag.text}</span>
        <strong>{target.name}</strong>
        {hasLanes && (
          <span className='ctb-rate'>
            <small>
              {t.champSelect.laneRate(laneName(my))} <b>{rate.toFixed(1)}%</b>
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
/** 누르면 클라이언트에서 그 챔피언을 고른다. 확정은 사용자가 직접 한다. */
function pickCounter(championId: number, rank: number, enemyId: number) {
  window.champSelect.pickChampion(championId);
  window.analytics.track('champ-select-counter', { champion: championId, rank, enemy: enemyId });
}

function CounterTiles({ target }: { target: CounterEnemy }) {
  if (!target.counters.length) {
    const main = target.positions[0];
    return (
      <p className='ct-empty'>
        {main
          ? t.champSelect.noCounters(target.name, laneName(main.position))
          : t.champSelect.noCountersYet(target.name)}
      </p>
    );
  }

  return (
    <ol className='ctb-tiles'>
      {target.counters.map((counter, at) => (
        <li key={counter.championId}>
          <button
            type='button'
            title={counter.name}
            onClick={() => pickCounter(counter.championId, at + 1, target.championId)}
          >
            <img src={championTileUrl(counter.championId)} alt='' />
            <i>{at + 1}</i>
            <span>{counter.name}</span>
          </button>
        </li>
      ))}
    </ol>
  );
}

type Props = {
  counters: Counters;
  /** 내 챔피언을 확정했는지. 확정하면 카운터는 더 쓸 데가 없어 맞라인 한 줄로 접는다. */
  locked: boolean;
};

/** 맞라인으로 올 상대 한 명을 크게, 받아칠 챔피언을 큰 타일로. 다른 상대는 알약 탭. */
function CounterSection({ counters, locked }: Props) {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const { enemies, patch, myPosition: my } = counters;
  // 내 라인으로 올 확률이 높은 순. 맨 앞이 맞라인 유력
  const byLane = [...enemies].sort((a, b) => rateAt(b, my) - rateAt(a, my));
  // 새 상대가 공개돼도 고른 상대를 유지한다. 없으면 맞라인 유력부터
  const target = byLane.find((enemy) => enemy.championId === selectedId) ?? byLane[0];

  // 블루 1픽: 상대가 아직 아무도 공개하지 않았다
  if (!target) {
    return (
      <section className='ct' aria-label={t.champSelect.counterTitle}>
        <Head />
        <p className='ct-empty ct-wait'>
          <b>{t.champSelect.waitingEnemy}</b>
          <small>{t.champSelect.waitingEnemyDesc}</small>
        </p>
      </section>
    );
  }

  const hasLanes = patch !== null && target.positions.length > 0;

  if (locked) {
    const tag = laneTag(target, my);
    return (
      <section className='ct' aria-label={t.champSelect.likelyLaner}>
        <div className='ct-locked'>
          <img src={championIconUrl(target.championId)} alt='' />
          <span className={`ctb-tag ${tag.likely ? '' : 'ctb-tag-off'}`}>{tag.text}</span>
          <b>{target.name}</b>
          {hasLanes && (
            <small>
              {laneName(my)} {rateAt(target, my).toFixed(1)}%
            </small>
          )}
          <span className='ct-locked-note'>{t.champSelect.enemyCount(enemies.length)}</span>
        </div>
      </section>
    );
  }

  const note = [
    t.champSelect.revealed(Math.min(enemies.length, MAX_ENEMIES)),
    patch && t.champSelect.patch(patch),
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <section className='ct' aria-label={t.champSelect.counterTitle}>
      <Head note={note} />
      <EnemyCard target={target} my={my} hasLanes={hasLanes} />
      <CounterTiles target={target} />

      {/* 상대가 둘 이상일 때만 바꿔 볼 탭을 둔다 */}
      {byLane.length > 1 && (
        <div className='board-views ctb-switch' role='tablist' aria-label={t.champSelect.enemyTabs}>
          {byLane.map((other) => (
            <button
              key={other.championId}
              type='button'
              role='tab'
              aria-selected={other === target}
              title={other.name}
              onClick={() => {
                setSelectedId(other.championId);
                window.analytics.track('champ-select-counter-enemy', { enemy: other.championId });
              }}
            >
              <img src={championIconUrl(other.championId)} alt='' />
              {/* 상대가 늘면 칸이 좁아져 이름을 말줄임으로 자른다 */}
              <span className='ctb-switch-name'>{other.name}</span>
              {other.positions.length > 0 && <small>{Math.round(rateAt(other, my))}%</small>}
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

export default CounterSection;
