import './Bento.css';

import type {
  LcuCurrentRankedStats,
  MatchSummary,
  RankedEntry,
  Tier,
} from '../../../../shared/types';
import { StarIcon } from '../../../icons';
import { championIconUrl, championTileUrl } from '../../lib/ddragon';
import { formatWhen, queueLabel } from '../../lib/format';
import { formatRatio, kdaGrade, kdaValue } from '../../lib/kda';
import type { RecentSummary } from '../../lib/recentSummary';

// 500x500 정사각으로 잘린 에셋. ranked-emblem 쪽은 1280x720 와이드라 크게 쓰면 쪼그라든다.
const tierEmblemUrl = (tier: string) =>
  `https://raw.communitydragon.org/latest/plugins/rcp-fe-lol-shared-components/global/default/${
    tier ? tier.toLowerCase() : 'unranked'
  }.png`;

/** 랭크 칸 뒤에 번지는 빛. 엠블럼과 같은 계열이라 칸만 봐도 티어가 읽힌다. */
const TIER_GLOW: Record<Tier, string> = {
  '': '#6a707b',
  IRON: '#8d8a8f',
  BRONZE: '#c08a6a',
  SILVER: '#a9b6c6',
  GOLD: '#e3b34f',
  PLATINUM: '#4fc2c0',
  EMERALD: '#3fcf8e',
  DIAMOND: '#7fa6ff',
  MASTER: '#c77dff',
  GRANDMASTER: '#ff6b6b',
  CHALLENGER: '#f4d47c',
};

const PERCENT = 100;

const tierLabel = (tier: string) => tier.charAt(0) + tier.slice(1).toLowerCase();

function streakText(streak: number) {
  if (Math.abs(streak) < 2) return null;
  return streak > 0 ? `${streak}연승 중` : `${-streak}연패 중`;
}

function WinRateTile({ summary }: { summary: RecentSummary }) {
  const streak = streakText(summary.streak);

  return (
    <div className='tile tile-rate'>
      <span className='tile-label'>승률</span>

      <p className='rate-value'>
        {/* 0 에서 세어 올라간다. 읽는 사람용 값은 aria-label 에 따로 둔다. */}
        <span
          className='count'
          style={{ '--count': summary.winRate } as React.CSSProperties}
          aria-label={`${summary.winRate}`}
        />
        <small>%</small>
      </p>
      <p className='rate-record'>
        {summary.wins}승 {summary.losses}패
      </p>

      {/* 승패 흐름. 최근 판이 왼쪽 위다. 이긴 판은 채운 점, 진 판은 빈 고리. */}
      <ol className='dots' aria-label='최근 승패, 최근 판부터'>
        {summary.form.map((win, index) => (
          <li key={index} className={win ? 'dot-win' : 'dot-loss'} aria-label={win ? '승' : '패'} />
        ))}
      </ol>
      {streak && <p className={`streak ${summary.streak > 0 ? 'streak-win' : ''}`}>{streak}</p>}
    </div>
  );
}

function RankTile({ label, entry }: { label: string; entry: RankedEntry | undefined }) {
  const tier = entry?.tier ?? '';
  const played = (entry?.wins ?? 0) + (entry?.losses ?? 0);

  let title = '언랭크';
  let detail: string | null = null;
  if (entry?.isProvisional) {
    const done = entry.provisionalGameThreshold - entry.provisionalGamesRemaining;
    title = `배치 ${done}/${entry.provisionalGameThreshold}`;
    detail = `${entry.provisionalGamesRemaining}판 남음`;
  } else if (entry?.tier) {
    title = `${tierLabel(entry.tier)}${entry.division === 'NA' ? '' : ` ${entry.division}`}`;
    const winRate = played > 0 ? Math.round((entry.wins / played) * PERCENT) : 0;
    detail = `${entry.leaguePoints} LP · 승률 ${winRate}%`;
  }

  return (
    <div
      className='tile tile-rank'
      style={{ '--tier': TIER_GLOW[tier] } as React.CSSProperties}
      title={played > 0 ? `${entry?.wins}승 ${entry?.losses}패` : undefined}
    >
      <span className='tile-label'>{label}</span>
      <span className='rank-title'>
        {title}
        {entry?.miniSeriesProgress && <em className='rank-series'>승급전</em>}
      </span>
      {detail && <span className='rank-detail'>{detail}</span>}

      <img className='rank-emblem' src={tierEmblemUrl(tier)} alt='' />
    </div>
  );
}

function KdaTile({ summary }: { summary: RecentSummary }) {
  return (
    <div className='tile'>
      <span className='tile-label'>KDA</span>
      <span className={`kda-value ${kdaGrade(summary.kda)}`}>{formatRatio(summary.kda)}</span>
      <span className='tile-sub'>
        {summary.kills.toFixed(1)} / {summary.deaths.toFixed(1)} / {summary.assists.toFixed(1)}
      </span>
    </div>
  );
}

function ChampionsTile({ summary }: { summary: RecentSummary }) {
  return (
    <div className='tile'>
      <span className='tile-label'>모스트 챔피언</span>
      <ul className='champions'>
        {summary.champions.map(({ championId, games, winRate }) => (
          <li key={championId} title={`${games}판`}>
            <img src={championIconUrl(championId)} alt='' />
            <span>{winRate}%</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** 최근 판 중 가장 잘한 한 판. 숫자 요약 사이에 다시 보고 싶은 장면을 하나 둔다. */
function BestTile({ match }: { match: MatchSummary }) {
  const { stats } = match;
  const ratio = kdaValue(stats);

  return (
    <div className='tile tile-best'>
      <img className='best-art' src={championTileUrl(match.championId)} alt='' />

      <div className='best-body'>
        <span className='best-label'>
          <StarIcon />
          최고의 한 판
        </span>
        <span className='best-kda'>
          {stats.kills} <i>/</i> {stats.deaths} <i>/</i> {stats.assists}
        </span>
        <span className='tile-sub'>
          평점 <b>{formatRatio(ratio)}</b> · {queueLabel(match)} ·{' '}
          {formatWhen(match.gameCreationDate)}
        </span>
      </div>
    </div>
  );
}

type Props = {
  summary: RecentSummary | null;
  rankInfo: LcuCurrentRankedStats | null;
};

/**
 * 마우스가 올라간 칸에 커서 위치를 알려 준다. 칸 안의 빛이 그 자리를 따라다닌다.
 * 칸마다 핸들러를 달지 않고 판 하나에서 받아, 커서 아래 칸에만 값을 쓴다.
 */
function followPointer(event: React.PointerEvent<HTMLElement>) {
  const tile = (event.target as HTMLElement).closest<HTMLElement>('.tile');
  if (!tile) return;

  const box = tile.getBoundingClientRect();
  tile.style.setProperty('--pointer-x', `${event.clientX - box.left}px`);
  tile.style.setProperty('--pointer-y', `${event.clientY - box.top}px`);
}

/** 크기가 다른 칸을 짜 맞춘 통계 판. 가장 궁금한 승률이 가장 큰 칸을 쓴다. */
function Bento({ summary, rankInfo }: Props) {
  return (
    <section className='bento' aria-label='요약' onPointerMove={followPointer}>
      {summary && <WinRateTile summary={summary} />}
      <RankTile label='솔로랭크' entry={rankInfo?.queueMap.RANKED_SOLO_5x5} />
      <RankTile label='자유랭크' entry={rankInfo?.queueMap.RANKED_FLEX_SR} />
      {summary && (
        <>
          <KdaTile summary={summary} />
          <ChampionsTile summary={summary} />
          <BestTile match={summary.best} />
        </>
      )}
    </section>
  );
}

export default Bento;
