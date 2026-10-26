import type {
  RecommendedRunePage,
  RuneApplyError,
  RuneEntry,
  RuneRecommendation,
} from '../../../../shared/types';
import { t } from '../../../i18n';
import { Hatch } from '../MatchSection/parts';
import { pageLabel, RuneIcon } from './parts';

const PERCENT = 100;

/** 룬 한 줄: 그림 + 이름. 게임 화면에서 위에서부터 눌러 가는 순서 그대로 둔다. */
function RuneLine({ rune }: { rune: RuneEntry }) {
  return (
    <li className='csr-line'>
      <RuneIcon rune={rune} size={24} />
      {rune.name}
    </li>
  );
}

/** 계열 이름. 그림 주소는 추천 응답이 준다. */
function Tree({ style }: { style: RuneEntry }) {
  return (
    <span className='csr-tree'>
      <RuneIcon rune={style} size={14} />
      {style.name}
    </span>
  );
}

/**
 * 고른 페이지를 게임 룬 화면 순서대로 세로로 편다. 왼쪽 주 계열, 오른쪽 보조 계열 + 파편.
 * 바탕은 '내 플레이' 칸과 같은 판 위에 두고, 1순위면 금색 빛을 더한다.
 */
function RunePageView({ page, topRate }: { page: RecommendedRunePage; topRate: number }) {
  return (
    <div className={`csr-page ${page.rank === 1 ? 'csr-page-top' : ''}`}>
      <section className='csr-col'>
        <Tree style={page.primaryStyle} />
        <span className='csr-key'>
          <RuneIcon rune={page.keystone} size={42} className='cs-keystone' />
          <b>{page.keystone.name}</b>
        </span>
        <ol className='csr-lines'>
          {page.primaryRunes.map((rune) => (
            <RuneLine key={rune.id} rune={rune} />
          ))}
        </ol>
      </section>

      <section className='csr-col'>
        <Tree style={page.subStyle} />
        <ol className='csr-lines'>
          {page.subRunes.map((rune) => (
            <RuneLine key={rune.id} rune={rune} />
          ))}
        </ol>
        <span className='csr-shards'>
          {page.statShards.map((shard, at) => (
            <RuneIcon key={`${shard.id}-${at}`} rune={shard} size={16} />
          ))}
        </span>
        {/* 이 묶음을 얼마나 썼는지. 1순위를 꽉 찬 막대로 두고 나머지를 견준다 */}
        <span className='csr-rate'>
          <span className='csr-rate-bar'>
            <Hatch percent={(page.evidence.pickRate / topRate) * PERCENT} from='left' />
          </span>
          <small>{t.champSelect.games(page.evidence.games)}</small>
        </span>
      </section>
    </div>
  );
}

function Empty({ title, note }: { title: string; note: string }) {
  return (
    <p className='csr-empty'>
      <b>{title}</b>
      <small>{note}</small>
    </p>
  );
}

const ERROR_TEXT: Record<RuneApplyError, string> = {
  'no-slot': t.champSelect.noSlot,
  failed: t.champSelect.applyFailed,
};

type Props = {
  /** 챔피언을 고르기 전이면 null */
  myChampionId: number | null;
  runes: RuneRecommendation | null;
  /** 지금 보고 있는(= 클라이언트에 넣은) 페이지 순위 */
  selectedRank: number;
  /** 프로 룬을 넣어 둔 동안은 어느 탭도 들어간 룬이 아니라 선택 표시를 끈다 */
  proInUse: boolean;
  onSelect: (rank: number) => void;
  error: RuneApplyError | null;
};

/** 챔피언을 고르면 1순위가 바로 들어가고, 탭을 바꾸면 그 페이지가 바로 들어간다. 고른 탭이 곧 들어간 룬이다. */
function RuneSection({ myChampionId, runes, selectedRank, proInUse, onSelect, error }: Props) {
  const pages = runes?.pages ?? [];
  const page = pages.find((candidate) => candidate.rank === selectedRank) ?? pages[0];

  const body = (() => {
    if (myChampionId === null)
      return <Empty title={t.champSelect.pickChampion} note={t.champSelect.pickChampionDesc} />;
    // 받는 중. 금방 오므로 자리만 비워 둔다
    if (!runes) return null;
    if (!page)
      return (
        <Empty
          title={t.champSelect.fewGames}
          note={t.champSelect.fewGamesDesc(runes.championGames)}
        />
      );

    return (
      <>
        {/* 전적 상세의 '피해량 / 아이템'과 같은 알약 탭 */}
        <div className='board-views csr-tabs' role='tablist' aria-label={t.champSelect.pageTabs}>
          {pages.map((other) => (
            <button
              key={other.rank}
              type='button'
              role='tab'
              aria-selected={!proInUse && other === page}
              onClick={() => onSelect(other.rank)}
            >
              <i className={`csr-rank ${other.rank === 1 ? 'csr-rank-top' : ''}`}>
                {pageLabel(other.rank)}
              </i>
              <RuneIcon rune={other.keystone} size={18} className='cs-keystone' />
              {Math.round(other.evidence.pickRate * PERCENT)}%
            </button>
          ))}
        </div>
        <RunePageView key={page.rank} page={page} topRate={pages[0].evidence.pickRate || 1} />
        {error && <p className='csr-error'>{ERROR_TEXT[error]}</p>}
      </>
    );
  })();

  return (
    <section className='csr' aria-label={t.champSelect.runeTitle}>
      <div className='section-head'>
        <h1 className='section-title'>{t.champSelect.runeTitle}</h1>
        {runes?.patch && page && (
          <span className='csr-meta'>
            {t.champSelect.runeMeta(runes.patch, runes.championGames)}
          </span>
        )}
      </div>
      {body}
    </section>
  );
}

export default RuneSection;
