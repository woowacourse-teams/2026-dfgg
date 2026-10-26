import { useState } from 'react';

import { Hatch } from '../home/components/MatchSection/parts';
import { Build, ContextBar, Credit, Icon, pageLabel, ProPhoto } from './champSelectParts';
import {
  ME,
  type Named,
  percent,
  type RunePage,
  RUNES,
  shortDate,
  TODAY,
  type TodayPick,
} from './data';
import { championIcon, championSplash, type Icons } from './icons';

/** 오늘의 픽을 고르는 상태. 첫 픽을 보여 준 채로 시작한다. */
function usePick() {
  const [index, setIndex] = useState(0);
  return { index, pick: TODAY.picks[index], setIndex };
}

/** 중계 화면 하단 자막처럼: 선수 한 명을 크게, 사진은 사선으로 잘라 오른쪽에 세운다. */
function SpotlightHero({ icons }: { icons: Icons }) {
  const { index, pick, setIndex } = usePick();

  return (
    <section className='csa-hero' aria-label='오늘의 픽'>
      <img className='csa-hero-bg' src={championSplash(pick.champion.id)} alt='' />
      <ProPhoto key={pick.player.name} pick={pick} className='csa-hero-photo' />

      <div className='csa-hero-body'>
        <span className='csa-tag'>
          <b>TODAY&apos;S PICK</b>
          <span>{TODAY.tournament.name}</span>
        </span>
        <strong className='csa-name'>{pick.player.name}</strong>
        <span className='csa-team'>{pick.player.team}</span>

        <span className='csa-champ'>
          <img src={championIcon(pick.champion.id)} alt='' />
          <b>{pick.champion.name}</b>
          <small>{shortDate(pick.playedAt)} 경기</small>
        </span>

        <span className='csa-loadout'>
          <Build pick={pick} icons={icons} size={20} />
        </span>
      </div>

      <ProRunes pick={pick} icons={icons} />

      {/* 다른 선수로 넘기는 줄. 사진 대신 챔피언 얼굴로 무엇을 골랐는지 먼저 보인다. */}
      <ol className='csa-strip'>
        {TODAY.picks.map((other, at) => (
          <li key={other.player.name}>
            <button
              type='button'
              className={at === index ? 'csa-strip-on' : ''}
              onClick={() => setIndex(at)}
            >
              <img src={championIcon(other.champion.id)} alt='' />
              <span>{other.player.name}</span>
            </button>
          </li>
        ))}
      </ol>
    </section>
  );
}

/**
 * 고른 선수가 실제로 쓴 룬 한 줄. 핵심 룬만 이름을 붙이고 나머지는 그림으로, 마우스를 올리면 이름이 뜬다.
 * 마음에 들면 그대로 내 룬 페이지에 넣는다.
 */
function ProRunes({ pick, icons }: { pick: TodayPick; icons: Icons }) {
  const { runes } = pick;

  return (
    <div className='csa-pro-runes'>
      <span className='csa-pro-key'>
        <Icon of={icons.rune} entry={runes.keystone} size={30} className='cs-keystone' />
        <span>
          <small>{pick.player.name}의 룬</small>
          <b>{runes.keystone.name}</b>
        </span>
      </span>

      <span className='csa-pro-set'>
        {runes.primary.map((rune) => (
          <Icon key={rune.id} of={icons.rune} entry={rune} size={20} />
        ))}
        <i className='csa-pro-gap' />
        <Icon of={icons.tree} entry={runes.secondaryTree} size={14} className='csa-pro-tree' />
        {runes.secondary.map((rune) => (
          <Icon key={rune.id} of={icons.rune} entry={rune} size={20} />
        ))}
        <i className='csa-pro-gap' />
        {runes.shards.map((shard, at) => (
          <Icon key={`${shard.id}-${at}`} of={icons.rune} entry={shard} size={14} />
        ))}
      </span>

      <button type='button' className='csa-pro-copy'>
        이 룬 쓰기
      </button>
    </div>
  );
}

const PERCENT = 100;
const topRate = RUNES.pages[0].evidence.pickRate;

/** 룬 한 줄: 그림 + 이름. 게임 화면에서 위에서부터 눌러 가는 순서 그대로 둔다. */
function RuneLine({ of, rune, size }: { of: Icons['rune']; rune: Named; size: number }) {
  return (
    <li className='csr-line'>
      <Icon of={of} entry={rune} size={size} />
      {rune.name}
    </li>
  );
}

/**
 * 고른 페이지를 게임 룬 화면 순서대로 세로로 편다. 왼쪽 주 계열, 오른쪽 보조 계열 + 파편.
 * 바탕은 '내 플레이' 칸과 같은 판 위에 두고, 1순위면 금색 빛을 더한다.
 */
function RunePageView({ page, icons }: { page: RunePage; icons: Icons }) {
  return (
    <div className={`csr-page ${page.rank === 1 ? 'csr-page-top' : ''}`}>
      <section className='csr-col'>
        <span className='csr-tree'>
          <Icon of={icons.tree} entry={page.primaryStyle} size={14} />
          {page.primaryStyle.name}
        </span>
        <span className='csr-key'>
          <Icon of={icons.rune} entry={page.keystone} size={42} className='cs-keystone' />
          <b>{page.keystone.name}</b>
        </span>
        <ol className='csr-lines'>
          {page.primaryRunes.map((rune) => (
            <RuneLine key={rune.id} of={icons.rune} rune={rune} size={24} />
          ))}
        </ol>
      </section>

      <section className='csr-col'>
        <span className='csr-tree'>
          <Icon of={icons.tree} entry={page.subStyle} size={14} />
          {page.subStyle.name}
        </span>
        <ol className='csr-lines'>
          {page.subRunes.map((rune) => (
            <RuneLine key={rune.id} of={icons.rune} rune={rune} size={24} />
          ))}
        </ol>
        <span className='csr-shards'>
          {page.statShards.map((shard, at) => (
            <Icon key={`${shard.id}-${at}`} of={icons.rune} entry={shard} size={16} />
          ))}
        </span>
        {/* 이 묶음을 얼마나 썼는지. 1순위를 꽉 찬 막대로 두고 나머지를 견준다 */}
        <span className='csr-rate'>
          <span className='csr-rate-bar'>
            <Hatch percent={(page.evidence.pickRate / topRate) * PERCENT} from='left' />
          </span>
          <small>{page.evidence.games.toLocaleString()}판</small>
        </span>
      </section>
    </div>
  );
}

/** 룬을 아직 못 보여 주는 때. 챔피언을 고르기 전이거나, 이번 패치 판 수가 100판이 안 될 때. */
export type RuneState = 'ready' | 'noChampion' | 'fewGames';

const RUNE_EMPTY: Record<Exclude<RuneState, 'ready'>, { title: string; note: string }> = {
  noChampion: { title: '챔피언을 고르면 룬을 추천해요', note: '고르는 즉시 1순위 룬이 들어가요' },
  fewGames: {
    title: '이번 패치 판 수가 아직 적어요',
    note: `${ME.champion.name} ${ME.position} 60판 · 100판부터 추천해요`,
  },
};

export function ChampSelect({
  icons,
  state = 'ready',
  counter,
}: {
  icons: Icons;
  state?: RuneState;
  /** 오늘의 픽과 룬 사이에 들어갈 카운터 추천. 시안마다 갈아 끼운다. */
  counter?: React.ReactNode;
}) {
  const [selected, setSelected] = useState(0);
  const page = RUNES.pages[selected];

  return (
    <div className='cs'>
      <ContextBar
        champion={state === 'noChampion' ? { id: 0, name: '고르는 중' } : ME.champion}
        position={ME.position}
      />
      <SpotlightHero icons={icons} />
      <Credit />
      {counter}

      <section className='csr' aria-label='룬 추천'>
        <div className='section-head'>
          <h1 className='section-title'>룬 추천</h1>
          {state === 'ready' && (
            <span className='csr-meta'>
              {ME.champion.name} {ME.position} · 패치 {RUNES.patch} ·{' '}
              {RUNES.championGames.toLocaleString()}판
            </span>
          )}
        </div>

        {state !== 'ready' ? (
          <p className='csr-empty'>
            <b>{RUNE_EMPTY[state].title}</b>
            <small>{RUNE_EMPTY[state].note}</small>
          </p>
        ) : (
          <>
            {/* 전적 상세의 '피해량 / 아이템'과 같은 알약 탭. 고른 탭이 곧 클라이언트에 들어간 룬이다 */}
            <div className='board-views csr-tabs' role='tablist' aria-label='룬 페이지'>
              {RUNES.pages.map((other, at) => (
                <button
                  key={other.rank}
                  type='button'
                  role='tab'
                  aria-selected={at === selected}
                  onClick={() => setSelected(at)}
                >
                  <i className={`csr-rank ${other.rank === 1 ? 'csr-rank-top' : ''}`}>
                    {pageLabel(other)}
                  </i>
                  <Icon of={icons.rune} entry={other.keystone} size={18} className='cs-keystone' />
                  {percent(other.evidence.pickRate)}
                </button>
              ))}
            </div>
            <RunePageView key={page.rank} page={page} icons={icons} />
          </>
        )}
      </section>
    </div>
  );
}
