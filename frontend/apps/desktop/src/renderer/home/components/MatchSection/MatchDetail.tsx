import './MatchDetail.css';

import * as Sentry from '@sentry/electron/renderer';
import { useEffect, useState } from 'react';

import type {
  DfScore,
  DfScores,
  MatchDetail as Detail,
  MatchParticipant,
  MatchStats,
  MatchSummary,
  MatchTeam,
} from '../../../../shared/types';
import { t } from '../../../i18n';
import { championIconUrl } from '../../lib/ddragon';
import { rankTone } from '../../lib/dfRank';
import { csPerMin, formatNumber, percentOf, perMinute } from '../../lib/format';
import { Hatch, ItemRow, KdaLine } from './parts';

const BLUE_TEAM_ID = 100;
const PERCENT = 100;

const RING_SIZE = 60;
const RING_STROKE = 5;
const RING_RADIUS = (RING_SIZE - RING_STROKE) / 2;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

type BoardView = 'damage' | 'items' | 'rank';

const BOARD_VIEWS: { key: BoardView; label: string }[] = [
  { key: 'damage', label: '피해량' },
  { key: 'items', label: '아이템' },
  { key: 'rank', label: 'DF 순위' },
];

const sum = (members: MatchParticipant[], pick: (stats: MatchStats) => number) =>
  members.reduce((total, member) => total + pick(member.stats), 0);

/** 맨 위 점수판. 중계 화면처럼 두 팀의 킬 수를 가운데서 맞댄다. */
function Scoreline({
  mine,
  theirs,
  myTeam,
  theirTeam,
}: {
  mine: MatchParticipant[];
  theirs: MatchParticipant[];
  myTeam: MatchTeam | undefined;
  theirTeam: MatchTeam | undefined;
}) {
  const myGold = sum(mine, (stats) => stats.goldEarned);
  const theirGold = sum(theirs, (stats) => stats.goldEarned);

  return (
    <header className='score'>
      <div className='score-board'>
        <div className={`score-side ${myTeam?.win ? 'score-win' : ''}`}>
          <b>{myTeam?.win ? t.result.win : t.result.loss}</b>
          <span>{t.match.myTeam}</span>
        </div>

        <p className='score-kills'>
          <strong className='score-mine'>{sum(mine, (stats) => stats.kills)}</strong>
          <i>{t.match.kills}</i>
          <strong>{sum(theirs, (stats) => stats.kills)}</strong>
        </p>

        <div className={`score-side score-right ${theirTeam?.win ? 'score-win' : ''}`}>
          <b>{theirTeam?.win ? t.result.win : t.result.loss}</b>
          <span>{t.match.theirTeam}</span>
        </div>
      </div>

      {/* 골드는 한 막대를 두 팀이 나눠 가진다. 어느 쪽이 얼마나 앞섰는지가 길이로 보인다. */}
      <div className='score-gold'>
        <span>{formatNumber(myGold)}</span>
        <span className='score-gold-track'>
          <i>{t.match.gold}</i>
          <span className='score-gold-bar'>
            <Hatch percent={percentOf(myGold, myGold + theirGold) || PERCENT / 2} from='left' />
          </span>
        </span>
        <span>{formatNumber(theirGold)}</span>
      </div>
    </header>
  );
}

/** 이 판에서 내가 한 몫. 팀과 견줘야 뜻이 생기는 숫자들이다. */
function MyPlay({
  me,
  team,
  duration,
  dfScore,
}: {
  me: MatchStats;
  team: MatchParticipant[];
  duration: number;
  dfScore: DfScore | undefined;
}) {
  // 어시스트 집계가 팀 킬 합계와 어긋나는 경우가 있어 100 을 넘지 않게 막는다.
  const killShare = Math.min(
    PERCENT,
    percentOf(
      me.kills + me.assists,
      sum(team, (stats) => stats.kills),
    ),
  );
  const damageRank = team.filter((member) => member.stats.damageDealt > me.damageDealt).length + 1;

  return (
    <section className='my-play' aria-label={t.match.myPlay}>
      {dfScore && (
        <div className={`my-df df-tone-${rankTone(dfScore.rank)}`}>
          <strong>
            {dfScore.rank}
            <small>등</small>
          </strong>
          <span>DF {dfScore.score.toFixed(1)}</span>
        </div>
      )}

      <div className='my-ring'>
        <svg
          width={RING_SIZE}
          height={RING_SIZE}
          viewBox={`0 0 ${RING_SIZE} ${RING_SIZE}`}
          aria-hidden
        >
          <circle className='my-ring-track' cx='50%' cy='50%' r={RING_RADIUS} />
          <circle
            className='my-ring-fill'
            cx='50%'
            cy='50%'
            r={RING_RADIUS}
            strokeDasharray={RING_LENGTH}
            strokeDashoffset={RING_LENGTH * (1 - killShare / PERCENT)}
            style={{ '--ring-length': RING_LENGTH } as React.CSSProperties}
          />
        </svg>
        <span className='my-ring-value'>
          {killShare}
          <small>%</small>
        </span>
        <span className='my-ring-label'>{t.match.killShare}</span>
      </div>

      {/* 칸마다 이름표 → 큰 숫자 → 알약 순서. 알약에는 그 숫자를 풀어 주는 한마디를 넣는다. */}
      <dl className='my-stats'>
        <div>
          <dt>{t.match.damageDealt}</dt>
          <dd>{formatNumber(me.damageDealt)}</dd>
          <dd className={`my-pill ${damageRank === 1 ? 'my-pill-top' : ''}`}>
            {t.match.damageRank(damageRank)}
          </dd>
        </div>
        <div>
          <dt>{t.match.csPerMin}</dt>
          <dd>{csPerMin(me.cs, duration)}</dd>
          <dd className='my-pill'>{t.match.csTotal(me.cs)}</dd>
        </div>
        <div>
          <dt>{t.match.gold}</dt>
          <dd>{formatNumber(me.goldEarned)}</dd>
          <dd className='my-pill'>{t.match.perMinute(perMinute(me.goldEarned, duration))}</dd>
        </div>
      </dl>
    </section>
  );
}

/** 맞대결 판의 한쪽 선수. 오른쪽은 CSS 로 좌우를 뒤집어 가운데를 보고 마주 서게 한다. */
function Fighter({
  player,
  side,
  isMe,
  view,
  maxDealt,
  version,
  dfRank,
}: {
  player: MatchParticipant | undefined;
  side: 'left' | 'right';
  isMe: boolean;
  view: Exclude<BoardView, 'rank'>;
  dfRank: number | undefined;
  maxDealt: number;
  version: string | null;
}) {
  // 팀 인원이 다르면 빈 자리로 줄을 맞춘다
  if (!player) return <div className='fighter' />;

  const { stats } = player;
  const isTopDamage = stats.damageDealt === maxDealt;

  return (
    <div className={`fighter fighter-${side} ${isMe ? 'fighter-me' : ''}`}>
      <span className='fighter-portrait'>
        <img src={championIconUrl(player.championId)} alt='' />
        {dfRank !== undefined && (
          <b className={`df-badge fighter-df df-tone-${rankTone(dfRank)}`}>{dfRank}</b>
        )}
        {/* 이 판에서 피해를 가장 많이 넣은 한 명. 이름 줄에 두면 긴 이름을 밀어내서 그림에 얹는다. */}
        {isTopDamage && <em>{t.match.topDamage}</em>}
      </span>

      <div className='fighter-body'>
        <div className='fighter-line'>
          <span className='fighter-name' title={`${player.gameName}#${player.tagLine}`}>
            {player.gameName}
          </span>
          <KdaLine stats={stats} />
        </div>

        {view === 'damage' ? (
          <div className={`fighter-damage ${isTopDamage ? 'fighter-damage-top' : ''}`}>
            {/* 가운데에서 바깥으로 자란다: 왼쪽 팀은 오른쪽 끝에서, 오른쪽 팀은 왼쪽 끝에서 */}
            <Hatch
              percent={percentOf(stats.damageDealt, maxDealt)}
              from={side === 'left' ? 'right' : 'left'}
            />
            <b>{formatNumber(stats.damageDealt)}</b>
          </div>
        ) : (
          <ItemRow items={stats.items} version={version} />
        )}
      </div>
    </div>
  );
}

/** DF 순위 보기. 두 팀을 섞어 1등부터 줄 세운다. 왼쪽 띠 색으로 우리 팀인지 가른다. */
function Ranking({
  players,
  scores,
  myTeamId,
  myId,
}: {
  players: MatchParticipant[];
  scores: DfScores;
  myTeamId: number;
  myId: number;
}) {
  const ranked = players
    .filter((player) => scores[player.participantId])
    .sort((a, b) => scores[a.participantId].rank - scores[b.participantId].rank);
  const topScore = Math.max(...ranked.map((player) => scores[player.participantId].score), 1);

  return (
    <ol className='ranking'>
      {ranked.map((player) => {
        const { rank, score } = scores[player.participantId];
        const isMine = player.teamId === myTeamId;
        return (
          <li
            key={player.participantId}
            className={`ranking-row ${isMine ? 'ranking-mine' : ''} ${
              player.participantId === myId ? 'ranking-me' : ''
            }`}
          >
            <b className={`df-badge df-tone-${rankTone(rank)}`}>{rank}</b>
            <img src={championIconUrl(player.championId)} alt='' />
            <span className='ranking-name' title={`${player.gameName}#${player.tagLine}`}>
              {player.gameName}
            </span>
            <span className='ranking-bar'>
              <Hatch percent={percentOf(score, topScore)} from='left' />
            </span>
            <span className='ranking-score'>{score.toFixed(1)}</span>
          </li>
        );
      })}
    </ol>
  );
}

type Props = {
  detail: Detail;
  summary: MatchSummary;
  version: string | null;
};

/** 펼쳤을 때 보이는 상세: 점수판 → 내 플레이 → 다섯 줄 맞대결. */
function MatchDetail({ detail, summary, version }: Props) {
  const [view, setView] = useState<BoardView>('damage');
  const [dfScores, setDfScores] = useState<DfScores | null>(null);

  // 못 받으면 순위 탭과 딱지만 빠지고 나머지 상세는 그대로 보인다.
  const { gameId } = detail;
  useEffect(() => {
    let cancelled = false;
    window.lcu
      .getDfScores(gameId)
      .then((scores) => {
        if (!cancelled) setDfScores(scores);
      })
      .catch((error) => Sentry.captureException(error));
    return () => {
      cancelled = true;
    };
  }, [gameId]);

  const me = detail.participants.find((player) => player.participantId === summary.participantId);
  const myTeamId = me?.teamId ?? detail.teams[0]?.teamId ?? BLUE_TEAM_ID;

  const myTeam = detail.teams.find((team) => team.teamId === myTeamId);
  // ponytail: 두 팀 게임만 맞대결로 그린다. 아레나처럼 팀이 셋 이상이면 첫 상대 팀만 보인다. 그 모드를 지원할 때 목록형으로 갈라야 한다.
  const theirTeam = detail.teams.find((team) => team.teamId !== myTeamId);

  const membersOf = (teamId: number | undefined) =>
    detail.participants.filter((player) => player.teamId === teamId);
  const mine = membersOf(myTeamId);
  const theirs = membersOf(theirTeam?.teamId);

  // 막대는 경기 전체에서 가장 높은 값을 기준으로 삼아야 팀끼리도 비교된다.
  const maxDealt = Math.max(...detail.participants.map((player) => player.stats.damageDealt), 1);
  const views = dfScores ? BOARD_VIEWS : BOARD_VIEWS.filter(({ key }) => key !== 'rank');
  const rows = Array.from({ length: Math.max(mine.length, theirs.length) }, (_, index) => index);

  return (
    <div className='detail'>
      <Scoreline mine={mine} theirs={theirs} myTeam={myTeam} theirTeam={theirTeam} />

      <MyPlay
        me={summary.stats}
        team={mine}
        duration={summary.gameDuration}
        dfScore={dfScores?.[summary.participantId]}
      />

      <section className='board'>
        <div className='board-head'>
          {/* 줄마다 피해량과 아이템을 다 넣으면 난잡하다. 하나씩 골라 본다. */}
          <div className='board-views' role='tablist' aria-label={t.match.viewLabel}>
            {views.map(({ key, label }) => (
              <button
                key={key}
                type='button'
                role='tab'
                aria-selected={view === key}
                onClick={() => setView(key)}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        {view === 'rank' && dfScores ? (
          <Ranking
            players={detail.participants}
            scores={dfScores}
            myTeamId={myTeamId}
            myId={summary.participantId}
          />
        ) : (
          <ol className='board-rows'>
            {rows.map((index) => (
              <li key={index} className='board-row'>
                <Fighter
                  player={mine[index]}
                  side='left'
                  isMe={mine[index]?.participantId === summary.participantId}
                  view={view === 'items' ? 'items' : 'damage'}
                  maxDealt={maxDealt}
                  version={version}
                  dfRank={dfScores?.[mine[index]?.participantId]?.rank}
                />
                <Fighter
                  player={theirs[index]}
                  side='right'
                  isMe={false}
                  view={view === 'items' ? 'items' : 'damage'}
                  maxDealt={maxDealt}
                  version={version}
                  dfRank={dfScores?.[theirs[index]?.participantId]?.rank}
                />
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}

export default MatchDetail;
