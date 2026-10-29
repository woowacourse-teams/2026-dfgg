import { useState } from 'react';

import { runeTreeOf } from '../../../../shared/runeTrees';
import { todayPickKey } from '../../../../shared/todayPicks';
import type { TodayPick, TodayPicks } from '../../../../shared/types';
import { t } from '../../../i18n';
import { championIconUrl, championSplashUrl } from '../../lib/ddragon';
import { Build, ProPhoto, RuneIcon, runeTreeIconUrl, shortDate } from './parts';

/**
 * 고른 선수가 실제로 쓴 룬 한 줄. 핵심 룬만 이름을 붙이고 나머지는 그림으로, 마우스를 올리면 이름이 뜬다.
 * '이 룬 쓰기'를 누르면 그대로 DFGG 룬 페이지에 넣는다.
 */
function ProRunes({ pick, inUse, onUse }: { pick: TodayPick; inUse: boolean; onUse: () => void }) {
  const { runes } = pick;
  // 응답에 트리가 비어 오면 보조 룬으로 트리를 찾는다
  const subTreeId = runes.secondaryTree?.id ?? runeTreeOf(runes.secondary[0]?.id ?? 0);
  const tree = subTreeId && runeTreeIconUrl(subTreeId);
  // 핵심 룬이나 트리를 알 수 없으면 넣을 수 없어 버튼을 감춘다 (메인의 fromPro 와 같은 조건)
  const usable = Boolean(
    runes.keystone && (runes.primaryTree?.id ?? runeTreeOf(runes.keystone.id)) && subTreeId,
  );

  return (
    <div className='csa-pro-runes'>
      {runes.keystone && (
        <span className='csa-pro-key'>
          <RuneIcon rune={runes.keystone} size={30} className='cs-keystone' />
          <span>
            <small>{t.champSelect.proRunes(pick.player.name)}</small>
            <b>{runes.keystone.name}</b>
          </span>
        </span>
      )}

      <span className='csa-pro-set'>
        {runes.primary.map((rune) => (
          <RuneIcon key={rune.id} rune={rune} size={20} />
        ))}
        <i className='csa-pro-gap' />
        {tree && <img className='cs-icon csa-pro-tree' src={tree} alt='' width={14} height={14} />}
        {runes.secondary.map((rune) => (
          <RuneIcon key={rune.id} rune={rune} size={20} />
        ))}
        <i className='csa-pro-gap' />
        {runes.shards.map((shard, at) => (
          <RuneIcon key={`${shard.id}-${at}`} rune={shard} size={14} />
        ))}
      </span>

      {usable && (
        <button
          type='button'
          className={`csa-pro-copy ${inUse ? 'csa-pro-copy-on' : ''}`}
          onClick={onUse}
          disabled={inUse}
        >
          {inUse ? t.champSelect.usingRunes : t.champSelect.useRunes}
        </button>
      )}
    </div>
  );
}

type Props = {
  todayPicks: TodayPicks;
  /** 지금 넣어 둔 프로 룬의 todayPickKey */
  proInUse: string | null;
  onUsePro: (key: string) => void;
};

type Slide = { index: number; dir: 'next' | 'prev' };

/**
 * 중계 화면 하단 자막처럼: 선수 한 명을 크게, 사진은 사선으로 잘라 오른쪽에 세운다.
 * 받은 20명을 카드 넘기듯 좌우 화살표로 넘긴다. 끝에서 넘기면 처음으로 돌아간다.
 */
function TodayPickHero({ todayPicks, proInUse, onUsePro }: Props) {
  const [slide, setSlide] = useState<Slide>({ index: 0, dir: 'next' });
  const { picks, tournament } = todayPicks;
  const total = picks.length;
  const index = Math.min(slide.index, total - 1);
  const pick = picks[index];
  if (!pick) return null;

  const show = (next: number, dir: Slide['dir'], how: 'arrow' | 'tick' | 'key') => {
    const target = (next + total) % total;
    setSlide({ index: target, dir });
    window.analytics.track('champ-select-today-pick', {
      player: picks[target].player.name,
      champion: picks[target].champion.id,
      how,
    });
  };
  const prev = (how: 'arrow' | 'key') => show(index - 1, 'prev', how);
  const next = (how: 'arrow' | 'key') => show(index + 1, 'next', how);

  return (
    <>
      <section
        className='csa-hero'
        aria-label={t.champSelect.todayPick}
        aria-roledescription='carousel'
        onKeyDown={(event) => {
          if (event.key === 'ArrowLeft') prev('key');
          if (event.key === 'ArrowRight') next('key');
        }}
      >
        {/* 넘길 때마다 새로 그려서 넘기는 방향으로 미끄러져 들어온다 */}
        <div
          key={todayPickKey(pick)}
          className={`csa-slide csa-slide-${slide.dir}`}
          aria-roledescription='slide'
          aria-label={t.champSelect.pickOf(index + 1, total)}
        >
          <img className='csa-hero-bg' src={championSplashUrl(pick.champion.id)} alt='' />
          <ProPhoto pick={pick} className='csa-hero-photo' />

          <div className='csa-hero-body'>
            <span className='csa-tag'>
              <b>{t.champSelect.todayPick}</b>
              {tournament && <span>{tournament.name}</span>}
            </span>
            <strong className='csa-name'>{pick.player.name}</strong>
            {pick.player.team && <span className='csa-team'>{pick.player.team}</span>}

            <span className='csa-champ'>
              <img src={championIconUrl(pick.champion.id)} alt='' />
              <b>{pick.champion.name}</b>
              <small>
                {t.champSelect.played(shortDate(pick.playedAt))}
                {pick.setNumber ? ` · ${t.champSelect.setNumber(pick.setNumber)}` : ''}
              </small>
            </span>

            <span className='csa-loadout'>
              <Build pick={pick} size={20} />
            </span>
          </div>

          <ProRunes
            pick={pick}
            inUse={proInUse === todayPickKey(pick)}
            onUse={() => onUsePro(todayPickKey(pick))}
          />
        </div>

        {/* 넘기는 줄: ‹ 진행 칸 n / 20 › */}
        {total > 1 && (
          <div className='csa-nav'>
            <button
              type='button'
              className='csa-arrow'
              onClick={() => prev('arrow')}
              aria-label={t.champSelect.prevPick}
            >
              <ArrowIcon dir='left' />
            </button>

            <ol className='csa-ticks' aria-hidden='true'>
              {picks.map((other, at) => (
                <li key={todayPickKey(other)}>
                  <button
                    type='button'
                    tabIndex={-1}
                    className={at === index ? 'csa-tick-on' : ''}
                    onClick={() => show(at, at < index ? 'prev' : 'next', 'tick')}
                  />
                </li>
              ))}
            </ol>

            <span className='csa-count'>
              <b>{index + 1}</b> / {total}
            </span>

            <button
              type='button'
              className='csa-arrow'
              onClick={() => next('arrow')}
              aria-label={t.champSelect.nextPick}
            >
              <ArrowIcon dir='right' />
            </button>
          </div>
        )}
      </section>
      <p className='cs-credit'>{t.champSelect.credit}</p>
    </>
  );
}

/** 화살표. 글자(‹ ›)는 글꼴마다 높이가 달라 가운데가 안 맞아서 선으로 그린다 */
function ArrowIcon({ dir }: { dir: 'left' | 'right' }) {
  return (
    <svg width='14' height='14' viewBox='0 0 14 14' aria-hidden='true'>
      <path
        d={dir === 'left' ? 'M8.5 2.5 4 7l4.5 4.5' : 'M5.5 2.5 10 7l-4.5 4.5'}
        fill='none'
        stroke='currentColor'
        strokeWidth='1.8'
        strokeLinecap='round'
        strokeLinejoin='round'
      />
    </svg>
  );
}

export default TodayPickHero;
