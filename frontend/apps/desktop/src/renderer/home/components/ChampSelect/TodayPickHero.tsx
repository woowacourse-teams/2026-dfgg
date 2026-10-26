import { useState } from 'react';

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
  const tree = runes.secondaryTree && runeTreeIconUrl(runes.secondaryTree.id);
  // 트리나 핵심 룬이 비어 오면 넣을 수 없어 버튼을 감춘다
  const usable = Boolean(runes.primaryTree && runes.secondaryTree && runes.keystone);

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
  proInUse: string | null;
  onUsePro: (playerName: string) => void;
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
        <ProPhoto key={pick.player.name} pick={pick} className='csa-hero-photo' />

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
          inUse={proInUse === pick.player.name}
          onUse={() => onUsePro(pick.player.name)}
        />

        {/* 다른 선수로 넘기는 줄. 사진 대신 챔피언 얼굴로 무엇을 골랐는지 먼저 보인다. */}
        {picks.length > 1 && (
          <ol className='csa-strip' style={{ '--count': picks.length } as React.CSSProperties}>
            {picks.map((other, at) => (
              <li key={other.player.name}>
                <button
                  type='button'
                  className={other === pick ? 'csa-strip-on' : ''}
                  onClick={() => setIndex(at)}
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
