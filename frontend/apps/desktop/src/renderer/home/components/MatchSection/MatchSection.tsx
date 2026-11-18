import './MatchSection.css';

import * as Sentry from '@sentry/electron/renderer';
import { useEffect, useState } from 'react';

import type {
  DfScore,
  HighlightGame,
  HighlightSummary,
  MatchDetail as Detail,
  MatchSummary,
} from '../../../../shared/types';
import { t } from '../../../i18n';
import { ChevronIcon, ClockIcon } from '../../../icons';
import { championTileUrl } from '../../lib/ddragon';
import { rankTone } from '../../lib/dfRank';
import { formatMinutes, formatWhen, queueLabel } from '../../lib/format';
import DfRankCard from '../DfRankCard/DfRankCard';
import HighlightNotice from '../HighlightSection/HighlightNotice';
import HighlightPlayer, { formatClock } from '../HighlightSection/HighlightPlayer';
import MatchDetail from './MatchDetail';
import { ItemRow, KdaLine, RatioChip } from './parts';

export function MatchCard({
  match,
  version,
  highlight,
  dfRank,
}: {
  match: MatchSummary;
  version: string | null;
  highlight?: HighlightSummary;
  /** 방금 끝난 판이면 가운데에 DF 등수를 크게 놓고 KDA 를 오른쪽으로 민다 */
  dfRank?: { score: DfScore; total: number };
}) {
  const [expanded, setExpanded] = useState(false);
  const [detail, setDetail] = useState<Detail | null>(null);
  const [failed, setFailed] = useState(false);

  const [highlightOpen, setHighlightOpen] = useState(false);
  const [highlightGame, setHighlightGame] = useState<HighlightGame | null | undefined>(undefined);

  const { stats } = match;

  const toggleHight = () => {
    const next = !highlightOpen;
    setHighlightOpen(next);
    if (next)
      window.analytics.track('highlight-open', { queue: match.queueName, source: 'match-card' });

    if (!next || highlightGame !== undefined) return;
    window.highlight
      .get(match.gameId)
      .then(setHighlightGame)
      .catch((error) => {
        setHighlightGame(null);
        Sentry.captureException(error);
      });
  };

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

  const summaryRow = (
    <>
      {/* 챔피언 그림을 카드 왼쪽에 비스듬히 잘라 건다 */}
      <span className='match-art'>
        <img src={championTileUrl(match.championId)} alt='' loading='lazy' />
      </span>

      {dfRank && <DfRankCard score={dfRank.score} total={dfRank.total} />}

      <span className={`match-info ${dfRank ? 'match-df-side' : ''}`}>
        <span className='match-top'>
          <b className='match-result'>{match.win ? t.result.win : t.result.loss}</b>
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

      {/* 방금 끝난 판은 KDA 를 오른쪽에 크게 두고 아이템은 상세에서 본다 */}
      {!dfRank && <ItemRow items={stats.items} version={version} />}
    </>
  );

  return (
    <li
      className={`match ${match.win ? 'match-win' : 'match-loss'} ${
        dfRank ? `match-df df-tone-${rankTone(dfRank.score.rank)}` : ''
      }`}
    >
      {dfRank ? (
        // 방금 끝난 판은 요약만 보여 준다. 세부는 아래 전적 목록에서 펼친다.
        <div className='match-card match-card-static'>{summaryRow}</div>
      ) : (
        <button type='button' className='match-card' onClick={toggle} aria-expanded={expanded}>
          {summaryRow}
          <span className='match-arrow' aria-hidden='true'>
            <ChevronIcon />
          </span>
        </button>
      )}

      {highlight && (
        <button
          type='button'
          className='match-highlight'
          onClick={toggleHight}
          aria-expanded={highlightOpen}
        >
          {t.match.highlight(highlight.count, formatClock(highlight.duration))}
        </button>
      )}

      {highlightOpen && (
        <div className='match-highlight-player'>
          {highlightGame ? (
            <HighlightPlayer game={highlightGame} autoPlay source='match-card' />
          ) : (
            <HighlightNotice kind={highlightGame === null ? 'deleted' : 'loading'} />
          )}
        </div>
      )}

      {expanded && (
        <div className='match-detail'>
          {/* 높이를 0에서 펼치려면 안쪽 상자가 넘치는 부분을 잘라 줘야 한다 */}
          <div className='match-detail-inner'>
            {detail ? (
              <MatchDetail detail={detail} summary={match} version={version} />
            ) : (
              <p className='matches-empty'>{failed ? t.match.loadFailed : t.common.loading}</p>
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
  const [highlights, setHighlights] = useState<Record<number, HighlightSummary>>({});

  useEffect(() => {
    if (!matches?.length) return;
    const ids = matches.map((m) => m.gameId);

    window.highlight.summary(ids).then(setHighlights);
  }, [matches]);

  return (
    <section className='matches'>
      <div className='section-head'>
        <h1 className='section-title'>{t.match.title}</h1>
      </div>

      {matches === null && <p className='matches-empty'>{t.match.loading}</p>}
      {matches?.length === 0 && <p className='matches-empty'>{t.match.empty}</p>}

      <ul className='match-list'>
        {matches?.map((match) => (
          <MatchCard
            key={match.gameId}
            match={match}
            version={version}
            highlight={highlights[match.gameId]}
          />
        ))}
      </ul>
    </section>
  );
}

export default MatchSection;
