import './MayhemChampSelect.css';

import { useMemo, useState } from 'react';

import type { AugmentRarity, ChampSelectState, MayhemAugment } from '../../../../shared/types';
import { t } from '../../../i18n';
import { championIconUrl, championSplashUrl } from '../../lib/ddragon';

const RARITIES: AugmentRarity[] = ['silver', 'gold', 'prismatic'];
/** 위에 크게 세우는 개수 */
const PODIUM = 3;

/** 순위가 있는 것부터 높은 순으로. 순위가 없는 증강은 뒤로 보낸다 */
const byRank = (a: MayhemAugment, b: MayhemAugment) =>
  (a.rank ?? Number.POSITIVE_INFINITY) - (b.rank ?? Number.POSITIVE_INFINITY);

const formatRate = (rate: number | null) => (rate === null ? '-' : `${rate.toFixed(1)}%`);

const ratio = (rate: number | null, max: number) =>
  rate !== null && max > 0 ? `${(rate / max) * 100}%` : '0%';

/** 머리 오른쪽: 등급별 1위 요약. 누르면 그 등급 탭으로 간다 */
function TierTops({
  groups,
  active,
  onSelect,
}: {
  groups: Record<AugmentRarity, MayhemAugment[]>;
  active: AugmentRarity;
  onSelect: (rarity: AugmentRarity) => void;
}) {
  return (
    <ul className='mcs-tops'>
      {RARITIES.map((key) => {
        const top = groups[key][0];
        if (!top) return null;
        return (
          <li key={key}>
            <button
              type='button'
              className={`mcs-top mcs-tab-${key}`}
              aria-pressed={key === active}
              onClick={() => onSelect(key)}
            >
              <i className='mcs-gem' aria-hidden />
              <img className='mcs-icon' src={top.iconUrl} alt='' />
              <b className='mcs-name'>{top.name}</b>
              <span className='mcs-top-rate'>{formatRate(top.pickRate)}</span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

/** 1~3위: 그림을 크게 세운 카드 */
function PodiumCard({ augment, maxRate }: { augment: MayhemAugment; maxRate: number }) {
  const { rank, name, iconUrl, pickRate } = augment;
  return (
    <li className={`mcs-card${rank === 1 ? ' mcs-best' : ''}`}>
      <span className='mcs-rank'>{rank ?? '-'}</span>
      <span className='mcs-card-art'>
        <img className='mcs-icon' src={iconUrl} alt='' />
      </span>
      <b className='mcs-name'>{name}</b>
      <span className='mcs-card-rate'>
        <small>{t.mayhemSelect.pickRate}</small>
        <b>{formatRate(pickRate)}</b>
      </span>
      <span className='mcs-bar' aria-hidden>
        <span style={{ width: ratio(pickRate, maxRate) }} />
      </span>
    </li>
  );
}

/** 4위부터: 한 줄 */
function AugmentRow({ augment, maxRate }: { augment: MayhemAugment; maxRate: number }) {
  const { rank, name, iconUrl, pickRate } = augment;
  return (
    <li className='mcs-row'>
      <span className='mcs-rank'>{rank ?? '-'}</span>
      <img className='mcs-icon' src={iconUrl} alt='' loading='lazy' />
      <b className='mcs-name'>{name}</b>
      <span className='mcs-bar' aria-hidden>
        <span style={{ width: ratio(pickRate, maxRate) }} />
      </span>
      <span className='mcs-rate-value'>{formatRate(pickRate)}</span>
    </li>
  );
}

/** 아수라장 밴픽 화면: 내 챔피언의 추천 증강을 실버 · 골드 · 프리즘으로 나눠 보여 준다 */
function MayhemChampSelectSection({ state }: { state: ChampSelectState }) {
  const [rarity, setRarity] = useState<AugmentRarity>('silver');
  const { myChampionId, mayhemAugments } = state;

  const groups = useMemo(() => {
    const list = mayhemAugments?.augments ?? [];
    return Object.fromEntries(
      RARITIES.map((key) => [key, list.filter((a) => a.rarity === key).sort(byRank)]),
    ) as Record<AugmentRarity, MayhemAugment[]>;
  }, [mayhemAugments]);

  const shown = groups[rarity];
  const maxRate = Math.max(0, ...shown.map((a) => a.pickRate ?? 0));
  const podium = shown.slice(0, PODIUM);
  const rest = shown.slice(PODIUM);

  const select = (next: AugmentRarity) => {
    setRarity(next);
    window.analytics.track('mayhem-select-rarity', { rarity: next });
  };

  return (
    // 등급마다 색(--tier)이 바뀌어 탭·테두리·빛이 함께 물든다
    <div className={`cs mcs mcs-tier-${rarity}`}>
      <header className='mcs-hero'>
        {myChampionId !== null && (
          <img
            key={myChampionId}
            className='mcs-hero-bg'
            src={championSplashUrl(myChampionId)}
            alt=''
          />
        )}
        <div className='mcs-hero-body'>
          <span className='cs-phase'>{t.mayhemSelect.phase}</span>
          <div className='mcs-hero-title'>
            {myChampionId !== null && (
              <img className='mcs-hero-champ' src={championIconUrl(myChampionId)} alt='' />
            )}
            <h1>{t.mayhemSelect.title}</h1>
          </div>
          {mayhemAugments && (
            <span className='mcs-hero-meta'>{t.mayhemSelect.patch(mayhemAugments.patch)}</span>
          )}
        </div>
        {myChampionId !== null && mayhemAugments && (
          <TierTops key={myChampionId} groups={groups} active={rarity} onSelect={select} />
        )}
      </header>

      {myChampionId === null ? (
        <p className='csr-empty'>
          <b>{t.mayhemSelect.waitChampion}</b>
          <small>{t.mayhemSelect.waitChampionDesc}</small>
        </p>
      ) : (
        <>
          <div className='mcs-tabs' role='tablist' aria-label={t.mayhemSelect.rarityTabs}>
            {RARITIES.map((key) => (
              <button
                key={key}
                type='button'
                role='tab'
                aria-selected={key === rarity}
                className={`mcs-tab mcs-tab-${key}`}
                onClick={() => select(key)}
              >
                <i className='mcs-gem' aria-hidden />
                {t.mayhemSelect.rarity[key]}
                <small>{groups[key].length}</small>
              </button>
            ))}
          </div>

          {!mayhemAugments ? (
            <p className='mcs-loading'>{t.common.loading}</p>
          ) : shown.length === 0 ? (
            <p className='csr-empty'>
              <b>{t.mayhemSelect.noData}</b>
            </p>
          ) : (
            // 챔피언이나 등급이 바뀌면 새로 그려 다시 떠오르게 한다
            <div key={`${myChampionId}-${rarity}`} className='mcs-board'>
              <ol className='mcs-podium'>
                {podium.map((augment) => (
                  <PodiumCard key={augment.augmentId} augment={augment} maxRate={maxRate} />
                ))}
              </ol>
              {rest.length > 0 && (
                <ol className='mcs-list'>
                  {rest.map((augment) => (
                    <AugmentRow key={augment.augmentId} augment={augment} maxRate={maxRate} />
                  ))}
                </ol>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}

export default MayhemChampSelectSection;
