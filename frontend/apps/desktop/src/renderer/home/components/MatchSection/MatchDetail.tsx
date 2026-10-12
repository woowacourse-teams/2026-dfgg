import './MatchDetail.css';

import { useState } from 'react';

import type {
  MatchDetail as Detail,
  MatchParticipant,
  MatchStats,
  MatchSummary,
  MatchTeam,
} from '../../../../shared/types';
import { championIconUrl } from '../../lib/ddragon';
import { csPerMin, formatNumber, percentOf, perMinute } from '../../lib/format';
import { Hatch, ItemRow, KdaLine } from './parts';

const BLUE_TEAM_ID = 100;
const PERCENT = 100;

const RING_SIZE = 60;
const RING_STROKE = 5;
const RING_RADIUS = (RING_SIZE - RING_STROKE) / 2;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

type BoardView = 'damage' | 'items';

const BOARD_VIEWS: { key: BoardView; label: string }[] = [
  { key: 'damage', label: '피해량' },
  { key: 'items', label: '아이템' },
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
          <b>{myTeam?.win ? '승리' : '패배'}</b>
          <span>우리 팀</span>
        </div>

        <p className='score-kills'>
          <strong className='score-mine'>{sum(mine, (stats) => stats.kills)}</strong>
          <i>킬</i>
          <strong>{sum(theirs, (stats) => stats.kills)}</strong>
        </p>

        <div className={`score-side score-right ${theirTeam?.win ? 'score-win' : ''}`}>
          <b>{theirTeam?.win ? '승리' : '패배'}</b>
          <span>상대 팀</span>
        </div>
      </div>

      {/* 골드는 한 막대를 두 팀이 나눠 가진다. 어느 쪽이 얼마나 앞섰는지가 길이로 보인다. */}
      <div className='score-gold'>
        <span>{formatNumber(myGold)}</span>
        <span className='score-gold-track'>
          <i>골드</i>
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
}: {
  me: MatchStats;
  team: MatchParticipant[];
  duration: number;
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
    <section className='my-play' aria-label='내 플레이'>
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
        <span className='my-ring-label'>킬 관여</span>
      </div>

      {/* 칸마다 이름표 → 큰 숫자 → 알약 순서. 알약에는 그 숫자를 풀어 주는 한마디를 넣는다. */}
      <dl className='my-stats'>
        <div>
          <dt>가한 피해</dt>
          <dd>{formatNumber(me.damageDealt)}</dd>
          <dd className={`my-pill ${damageRank === 1 ? 'my-pill-top' : ''}`}>
            팀 내 {damageRank}위
          </dd>
        </div>
        <div>
          <dt>분당 CS</dt>
          <dd>{csPerMin(me.cs, duration)}</dd>
          <dd className='my-pill'>총 {me.cs}개</dd>
        </div>
        <div>
          <dt>골드</dt>
          <dd>{formatNumber(me.goldEarned)}</dd>
          <dd className='my-pill'>분당 {perMinute(me.goldEarned, duration)}</dd>
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
}: {
  player: MatchParticipant | undefined;
  side: 'left' | 'right';
  isMe: boolean;
  view: BoardView;
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
        {/* 이 판에서 피해를 가장 많이 넣은 한 명. 이름 줄에 두면 긴 이름을 밀어내서 그림에 얹는다. */}
        {isTopDamage && <em>최다 딜</em>}
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

type Props = {
  detail: Detail;
  summary: MatchSummary;
  version: string | null;
};

/** 펼쳤을 때 보이는 상세: 점수판 → 내 플레이 → 다섯 줄 맞대결. */
function MatchDetail({ detail, summary, version }: Props) {
  const [view, setView] = useState<BoardView>('damage');

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
  const rows = Array.from({ length: Math.max(mine.length, theirs.length) }, (_, index) => index);

  return (
    <div className='detail'>
      <Scoreline mine={mine} theirs={theirs} myTeam={myTeam} theirTeam={theirTeam} />

      <MyPlay me={summary.stats} team={mine} duration={summary.gameDuration} />

      <section className='board'>
        <div className='board-head'>
          {/* 줄마다 피해량과 아이템을 다 넣으면 난잡하다. 하나씩 골라 본다. */}
          <div className='board-views' role='tablist' aria-label='보기'>
            {BOARD_VIEWS.map(({ key, label }) => (
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

        <ol className='board-rows'>
          {rows.map((index) => (
            <li key={index} className='board-row'>
              <Fighter
                player={mine[index]}
                side='left'
                isMe={mine[index]?.participantId === summary.participantId}
                view={view}
                maxDealt={maxDealt}
                version={version}
              />
              <Fighter
                player={theirs[index]}
                side='right'
                isMe={false}
                view={view}
                maxDealt={maxDealt}
                version={version}
              />
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}

export default MatchDetail;
