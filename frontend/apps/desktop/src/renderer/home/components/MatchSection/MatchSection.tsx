import './MatchSection.css';

import * as Sentry from '@sentry/electron/renderer';
import { useState } from 'react';

import type { MatchDetail as Detail, MatchSummary } from '../../../../shared/types';
import { ChevronIcon, ClockIcon } from '../../../icons';
import { championTileUrl } from '../../lib/ddragon';
import { formatMinutes, formatWhen, queueLabel } from '../../lib/format';
import MatchDetail from './MatchDetail';
import { ItemRow, KdaLine, RatioChip } from './parts';

function MatchCard({ match, version }: { match: MatchSummary; version: string | null }) {
  const [expanded, setExpanded] = useState(false);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [failed, setFailed] = useState(false);

  const { stats } = match;

  const toggle = () => {
    const next = !expanded;
    setExpanded(next);
    if (next) window.analytics.track('match-expand', { queue: match.queueName });

    // 펼칠 때 한 번만 받아 둔다. 다시 접었다 펴면 요청이 나가지 않는다.
    if (!next || detail) return;

    window.lcu
      .getMatchDetail(match.gameId)
      .then(setDetail)
      .catch((error) => {
        setFailed(true);
        Sentry.captureException(error);
      });
  };

  return (
    <li className={`match ${match.win ? 'match-win' : 'match-loss'}`}>
      <button type='button' className='match-card' onClick={toggle} aria-expanded={expanded}>
        {/* 챔피언 그림을 카드 왼쪽에 비스듬히 잘라 건다 */}
        <span className='match-art'>
          <img src={championTileUrl(match.championId)} alt='' loading='lazy' />
        </span>

        <span className='match-info'>
          <span className='match-top'>
            <b className='match-result'>{match.win ? '승리' : '패배'}</b>
            <span className='match-queue'>{queueLabel(match)}</span>
            <span className='match-when'>{formatWhen(match.gameCreationDate)}</span>
          </span>

          <KdaLine stats={stats} />

          {/* 숫자만 던지지 않고 무슨 숫자인지 이름표를 붙인다 */}
          <span className='chips'>
            <RatioChip stats={stats} />
            <span className='chip'>
              CS <b>{stats.cs}</b>
            </span>
            <span className='chip'>
              <ClockIcon />
              <b>{formatMinutes(match.gameDuration)}</b>
            </span>
          </span>
        </span>

        <ItemRow items={stats.items} version={version} />

        <span className='match-arrow' aria-hidden='true'>
          <ChevronIcon />
        </span>
      </button>

      {expanded && (
        <div className='match-detail'>
          {/* 높이를 0에서 펼치려면 안쪽 상자가 넘치는 부분을 잘라 줘야 한다 */}
          <div className='match-detail-inner'>
            {detail ? (
              <MatchDetail detail={detail} summary={match} version={version} />
            ) : (
              <p className='matches-empty'>
                {failed ? '기록을 가져오지 못했습니다' : '불러오는 중...'}
              </p>
            )}
          </div>
        </div>
      )}
    </li>
  );
}

function MatchSection({
  matches,
  version,
}: {
  matches: MatchSummary[] | null;
  version: string | null;
}) {
  return (
    <section className='matches'>
      <div className='section-head'>
        <h1 className='section-title'>전적</h1>
      </div>

      {matches === null && <p className='matches-empty'>전적을 불러오는 중...</p>}
      {matches?.length === 0 && <p className='matches-empty'>최근 전적이 없습니다</p>}

      <ul className='match-list'>
        {matches?.map((match) => (
          <MatchCard key={match.gameId} match={match} version={version} />
        ))}
      </ul>
    </section>
  );
}

export default MatchSection;
