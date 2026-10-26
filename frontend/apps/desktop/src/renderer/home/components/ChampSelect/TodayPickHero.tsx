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
  /** 지금 넣어 둔 프로 룬의 선수 이름 */
  /** 지금 넣어 둔 프로 룬의 todayPickKey */
  proInUse: string | null;
  onUsePro: (key: string) => void;
};

/** 중계 화면 하단 자막처럼: 선수 한 명을 크게, 사진은 사선으로 잘라 오른쪽에 세운다. */
function TodayPickHero({ todayPicks, proInUse, onUsePro }: Props) {
  const [index, setIndex] = useState(0);
  const { picks, tournament } = todayPicks;
  const pick = picks[Math.min(index, picks.length - 1)];
  if (!pick) return null;

  return (
    <>
      <section className='csa-hero' aria-label={t.champSelect.todayPick}>
        <img className='csa-hero-bg' src={championSplashUrl(pick.champion.id)} alt='' />
        <ProPhoto key={todayPickKey(pick)} pick={pick} className='csa-hero-photo' />

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
            <small>{t.champSelect.played(shortDate(pick.playedAt))}</small>
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

        {/* 다른 선수로 넘기는 줄. 사진 대신 챔피언 얼굴로 무엇을 골랐는지 먼저 보인다. */}
        {picks.length > 1 && (
          <ol className='csa-strip' style={{ '--count': picks.length } as React.CSSProperties}>
            {picks.map((other, at) => (
              <li key={todayPickKey(other)}>
                <button
                  type='button'
                  className={other === pick ? 'csa-strip-on' : ''}
                  onClick={() => {
                    setIndex(at);
                    window.analytics.track('champ-select-today-pick', {
                      player: other.player.name,
                      champion: other.champion.id,
                    });
                  }}
                >
                  <img src={championIconUrl(other.champion.id)} alt='' />
                  <span>{other.player.name}</span>
                </button>
              </li>
            ))}
          </ol>
        )}
      </section>
      <p className='cs-credit'>{t.champSelect.credit}</p>
    </>
  );
}

export default TodayPickHero;
