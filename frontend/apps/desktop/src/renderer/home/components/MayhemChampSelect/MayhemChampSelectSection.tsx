import './MayhemChampSelect.css';

import { useMemo, useState } from 'react';

import type { AugmentRarity, ChampSelectState, MayhemAugment } from '../../../../shared/types';
import { t } from '../../../i18n';
import { championIconUrl, championSplashUrl } from '../../lib/ddragon';

const RARITIES: AugmentRarity[] = ['silver', 'gold', 'prismatic'];

/** 순위가 있는 것부터 높은 순으로. 순위가 없는 증강은 뒤로 보낸다 */
const byRank = (a: MayhemAugment, b: MayhemAugment) =>
  (a.rank ?? Number.POSITIVE_INFINITY) - (b.rank ?? Number.POSITIVE_INFINITY);

/** 위에 크게 세우는 개수 */
const PODIUM = 3;

const formatRate = (rate: number | null) => (rate === null ? '-' : `${rate.toFixed(1)}%`);

const ratio = (rate: number | null, max: number) =>
  rate !== null && max > 0 ? `${(rate / max) * 100}%` : '0%';

/**
 * 설명 속 수치(15%, 20~80, 6초, 1.5배 …)를 찾는다.
 * 숫자 앞의 +, 범위(~), 소수점, 뒤따르는 단위까지 한 덩어리로 본다.
 */
const NUMBER = /([+-]?\d+(?:\.\d+)?(?:\s*~\s*\d+(?:\.\d+)?)?(?:%|초|배|회|명|개)?)/g;

/** 설명을 글자 그대로 그리되, 수치만 강조한다. HTML 로 해석하지 않는다 */
function Description({ text, className }: { text: string; className: string }) {
  const parts = text.split(NUMBER);
  return (
    <p className={`mcs-desc ${className}`}>
      {parts.map((part, i) =>
        // split 에 괄호를 쓰면 홀수 번째가 찾은 수치다
        i % 2 === 1 ? (
          <b key={i} className='mcs-num'>
            {part}
          </b>
        ) : (
          part
        ),
      )}
    </p>
  );
}

/** 1~3위: 그림을 크게 세운 카드. 설명은 누르면 카드 줄 아래에 펼친다 */
function PodiumCard({
  augment,
  maxRate,
  open,
  onToggle,
}: {
  augment: MayhemAugment;
  maxRate: number;
  open: boolean;
  onToggle: () => void;
}) {
  const { rank, name, iconUrl, pickRate, description } = augment;
  return (
    <li>
      <button
        type='button'
        className={`mcs-card${rank === 1 ? ' mcs-best' : ''}${open ? ' mcs-card-open' : ''}`}
        aria-expanded={description ? open : undefined}
        disabled={!description}
        onClick={onToggle}
      >
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
        {description && <i className='mcs-chevron' aria-hidden />}
      </button>
    </li>
  );
}

/** 순위 강조 단계. 1위는 레드, 2~3위는 등급 색, 나머지는 회색 */
const tierOf = (rank: number | null) => (rank === 1 ? 'best' : rank !== null && rank <= 3 ? 'top' : 'rest');

/** 한 줄: 순위 · 그림 · 이름 · 설명 첫 줄 · 픽률. 누르면 아래에 설명 전체를 펼친다 */
function AugmentRow({
  augment,
  maxRate,
  open,
  onToggle,
}: {
  augment: MayhemAugment;
  maxRate: number;
  open: boolean;
  onToggle: () => void;
}) {
  const { rank, name, iconUrl, pickRate, description } = augment;
  const rate = (
    <span className='mcs-rate'>
      <b>{formatRate(pickRate)}</b>
      <span className='mcs-bar' aria-hidden>
        <span style={{ width: ratio(pickRate, maxRate) }} />
      </span>
    </span>
  );
  const head = (
    <>
      <span className='mcs-rank'>{rank ?? '-'}</span>
      <img className='mcs-icon' src={iconUrl} alt='' loading='lazy' />
      <b className='mcs-name'>{name}</b>
      {/* 첫 줄만 보이고 넘치면 줄인다. 펼치면 숨긴다 */}
      {description && !open ? (
        <Description text={description} className='mcs-preview' />
      ) : (
        <span />
      )}
      {rate}
    </>
  );

  return (
    <li className={`mcs-row mcs-row-${tierOf(rank)}${open ? ' mcs-row-open' : ''}`}>
      {description ? (
        <button type='button' className='mcs-row-head' aria-expanded={open} onClick={onToggle}>
          {head}
          <i className='mcs-chevron' aria-hidden />
        </button>
      ) : (
        <div className='mcs-row-head'>
          {head}
          <i className='mcs-chevron mcs-chevron-none' aria-hidden />
        </div>
      )}
      {description && open && <Description text={description} className='mcs-detail' />}
    </li>
  );
}

/** 아수라장 밴픽 화면: 내 챔피언의 추천 증강을 실버 · 골드 · 프리즘으로 나눠 보여 준다 */
function MayhemChampSelectSection({ state }: { state: ChampSelectState }) {
  const [rarity, setRarity] = useState<AugmentRarity>('silver');
  // 설명을 펼친 줄. 등급을 바꾸면 닫는다
  const [openId, setOpenId] = useState<number | null>(null);
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
  const openPodium = podium.find((a) => a.augmentId === openId);
  // 설명을 펼칠 때만 기록한다. 어느 순위·자리의 설명을 실제로 읽는지 본다
  const toggle = (augment: MayhemAugment, place: 'podium' | 'list') => {
    const opening = openId !== augment.augmentId;
    setOpenId(opening ? augment.augmentId : null);
    if (opening) {
      window.analytics.track('mayhem-augment-open', {
        rarity: augment.rarity,
        rank: augment.rank ?? -1, // 순위가 없으면 -1
        place,
      });
    }
  };

  const select = (next: AugmentRarity) => {
    setRarity(next);
    setOpenId(null);
    window.analytics.track('mayhem-select-rarity', { rarity: next });
  };

  return (
    // 등급마다 색(--tier)이 바뀌어 탭·테두리·수치·빛이 함께 물든다
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
                  <PodiumCard
                    key={augment.augmentId}
                    augment={augment}
                    maxRate={maxRate}
                    open={openId === augment.augmentId}
                    onToggle={() => toggle(augment, 'podium')}
                  />
                ))}
              </ol>
              {/* 누른 카드의 설명. 카드 줄 바로 아래에 한 칸으로 펼친다 */}
              {openPodium?.description && (
                <div className='mcs-podium-detail'>
                  <b className='mcs-name'>{openPodium.name}</b>
                  <Description text={openPodium.description} className='mcs-detail' />
                </div>
              )}
              {rest.length > 0 && (
                <ol className='mcs-list'>
                  {rest.map((augment) => (
                    <AugmentRow
                      key={augment.augmentId}
                      augment={augment}
                      maxRate={maxRate}
                      open={openId === augment.augmentId}
                      onToggle={() => toggle(augment, 'list')}
                    />
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
