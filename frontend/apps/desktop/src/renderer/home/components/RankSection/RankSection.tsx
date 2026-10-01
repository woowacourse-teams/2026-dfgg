import './RankSection.css';

import type { LcuCurrentRankedStats, RankedEntry } from '../../../../shared/types';

// 500x500 정사각으로 잘린 에셋. ranked-emblem 쪽은 1280x720 와이드라 크게 쓰면 쪼그라든다.
const tierEmblemUrl = (tier: string) =>
  `https://raw.communitydragon.org/latest/plugins/rcp-fe-lol-shared-components/global/default/${
    tier ? tier.toLowerCase() : 'unranked'
  }.png`;

/** 소환사의 협곡 랭크 큐. 큐가 늘면 여기에만 추가하면 된다. */
const RIFT_QUEUES = [
  { key: 'RANKED_SOLO_5x5', label: '솔로/듀오' },
  { key: 'RANKED_FLEX_SR', label: '자유 랭크' },
] as const;

function rankLines(entry: RankedEntry) {
  // 배치 중일 때
  if (entry.isProvisional) {
    const done = entry.provisionalGameThreshold - entry.provisionalGamesRemaining;
    return {
      tier: (
        <>
          배치 {done}/{entry.provisionalGameThreshold}
        </>
      ),
      record: <>{entry.provisionalGamesRemaining}판 남음</>,
    };
  }

  // 언랭일때
  if (!entry.tier) {
    return { tier: <span className='rank-unranked'>언랭크</span>, record: null };
  }

  const total = entry.wins + entry.losses;
  const winRate = total > 0 ? Math.round((entry.wins / total) * 1000) / 10 : 0;

  const tierLabel = entry.tier.charAt(0) + entry.tier.slice(1).toLowerCase();

  return {
    tier: (
      <>
        {tierLabel} {entry.division}
        <span className='rank-lp'> · {entry.leaguePoints} LP</span>
        {entry.miniSeriesProgress && <em className='rank-series'>승급전</em>}
      </>
    ),
    record: (
      <>
        {entry.wins}승 {entry.losses}패 · {winRate}%
      </>
    ),
  };
}

function RankCard({ label, entry }: { label: string; entry: RankedEntry | undefined }) {
  // 미사용 큐(빈 티어이면서 전적도 없음)는 아예 렌더하지 않는다.
  const played = (entry?.wins ?? 0) + (entry?.losses ?? 0);
  if (!entry || (!entry.tier && played === 0 && !entry.isProvisional)) return null;

  const { tier, record } = rankLines(entry);

  return (
    <li className='rank-card'>
      <img className='rank-emblem' src={tierEmblemUrl(entry.tier)} alt='' />

      <div className='rank-body'>
        <span className='rank-queue'>{label}</span>
        <span className='rank-tier'>{tier}</span>
      </div>

      {record && <span className='rank-record'>{record}</span>}
    </li>
  );
}

function RankSection({ rankInfo }: { rankInfo: LcuCurrentRankedStats | null }) {
  const queueMap = rankInfo?.queueMap;

  return (
    <section className='ranks'>
      <div className='ranks-head'>
        <h1 className='ranks-title'>랭크</h1>
        {rankInfo?.highestCurrentSeasonReachedTierSR && (
          <span className='ranks-peak'>
            이번 시즌 최고 {rankInfo.highestCurrentSeasonReachedTierSR}
          </span>
        )}
      </div>

      <ul className='rank-group'>
        {RIFT_QUEUES.map(({ key, label }) => (
          <RankCard key={key} label={label} entry={queueMap?.[key]} />
        ))}
      </ul>
    </section>
  );
}

export default RankSection;
