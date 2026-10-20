import { useState } from 'react';

import { Hatch } from '../home/components/MatchSection/parts';
import { ME, type Player, PLAYERS } from './data';
import { championIcon, championTile } from './icons';

const TOTAL = PLAYERS.length;
const PERCENT = 100;
const byRank = [...PLAYERS].sort((a, b) => a.rank - b.rank);
const myTeamRank =
  PLAYERS.filter((player) => player.team === 'mine')
    .sort((a, b) => a.rank - b.rank)
    .findIndex((player) => player.isMe) + 1;

/** 등수에 따라 다르게 부르는 이름. 시안 C에서만 쓴다. */
function rankTitle(rank: number) {
  if (rank === 1) return 'MVP';
  if (rank <= 3) return `TOP ${rank}`;
  if (rank <= 7) return `${rank}등`;
  return '다음 판엔';
}

/** 1등은 금색, 2·3등은 흰색, 나머지는 회색 딱지 */
const medal = (rank: number) => (rank === 1 ? 'gold' : rank <= 3 ? 'silver' : 'plain');

/** 방금 끝난 판의 하이라이트 자리. 시안마다 위에 올라가는 순위 블록만 다르다. */
function HighlightFrame({ children }: { children: React.ReactNode }) {
  return (
    <section className='pp-postgame'>
      <div className='section-head'>
        <h1 className='section-title'>방금 끝난 판</h1>
      </div>
      {children}
      <div className='pp-video' aria-hidden='true'>
        <span>▶</span>
        <small>하이라이트 · 클립 4개</small>
      </div>
    </section>
  );
}

/** A안: 큰 등수 하나. 가장 먼저 눈에 들어오는 건 "몇 등" 숫자다. */
export function PostGameA() {
  return (
    <HighlightFrame>
      <div className='pp-rank-a'>
        <p className='pp-rank-a-number'>
          {ME.rank}
          <small>등</small>
          <span>/ {TOTAL}명</span>
        </p>
        <div className='pp-rank-a-body'>
          <span className='pp-label'>DF 스코어</span>
          <strong>
            {ME.score.toFixed(1)}
            <small>점</small>
          </strong>
          <span className='pp-rank-a-note'>
            우리 팀 {myTeamRank}위 · <b>승리</b>
          </span>
        </div>
        <span className='pp-rank-a-gauge'>
          <Hatch percent={ME.score} from='left' />
        </span>
      </div>
    </HighlightFrame>
  );
}

/** B안: 10명을 점수 순으로 줄 세운다. 내 자리와 위아래 차이가 한눈에 보인다. */
export function PostGameB() {
  return (
    <HighlightFrame>
      <div className='pp-rank-b'>
        <p className='pp-rank-b-head'>
          <span>
            DF 스코어 <b>{ME.rank}등</b>
          </span>
          <span className='pp-rank-b-score'>{ME.score.toFixed(1)}점</span>
        </p>
        <ol className='pp-rank-b-line'>
          {byRank.map((player) => (
            <li
              key={player.championId}
              className={`pp-rank-b-player pp-team-${player.team} ${player.isMe ? 'pp-me' : ''}`}
              title={`${player.rank}등 · ${player.name} · ${player.score.toFixed(1)}점`}
            >
              <img src={championIcon(player.championId)} alt='' />
              <span>{player.isMe ? '나' : player.rank}</span>
            </li>
          ))}
        </ol>
        <p className='pp-rank-b-legend'>
          <span className='pp-dot pp-dot-mine' />
          우리 팀
          <span className='pp-dot pp-dot-theirs' />
          상대 팀
        </p>
      </div>
    </HighlightFrame>
  );
}

/** C안: 등수를 칭호로 바꿔 부른다. 내 챔피언 그림을 깔아 결과 화면처럼 보이게 한다. */
export function PostGameC() {
  return (
    <HighlightFrame>
      <div className={`pp-rank-c pp-medal-${medal(ME.rank)}`}>
        <img className='pp-rank-c-art' src={championTile(ME.championId)} alt='' />
        <div className='pp-rank-c-body'>
          <span className='pp-label'>이번 판 DF 스코어</span>
          <strong className='pp-rank-c-title'>{rankTitle(ME.rank)}</strong>
          <span className='pp-rank-c-meta'>
            {ME.score.toFixed(1)}점 · 10명 중 {ME.rank}등
          </span>
          {/* 1등부터 10등까지 열 칸. 내 칸만 붉게 칠한다. */}
          <ol className='pp-rank-c-ladder' aria-hidden='true'>
            {Array.from({ length: TOTAL }, (_, index) => (
              <li key={index} className={index + 1 === ME.rank ? 'pp-here' : ''} />
            ))}
          </ol>
        </div>
      </div>
    </HighlightFrame>
  );
}

function KdaText({ kda }: { kda: Player['kda'] }) {
  return (
    <span className='kda-line'>
      {kda[0]} <i>/</i> <b>{kda[1]}</b> <i>/</i> {kda[2]}
    </span>
  );
}

const maxDamage = Math.max(...PLAYERS.map((player) => player.damage));

/** 전적 상세의 맞대결 한 칸. 시안마다 등수를 어디에 붙이는지만 다르다. */
function Fighter({
  player,
  side,
  rankAt,
}: {
  player: Player | undefined;
  side: 'left' | 'right';
  rankAt: 'portrait' | 'name';
}) {
  if (!player) return <div className='fighter' />;

  return (
    <div className={`fighter fighter-${side} ${player.isMe ? 'fighter-me' : ''}`}>
      <span className='fighter-portrait'>
        <img src={championIcon(player.championId)} alt='' />
        {rankAt === 'portrait' && (
          <b className={`pp-rank-tag pp-medal-${medal(player.rank)}`}>{player.rank}</b>
        )}
      </span>
      <div className='fighter-body'>
        <div className='fighter-line'>
          <span className='pp-name-line'>
            <span className='fighter-name'>{player.name}</span>
            {rankAt === 'name' && (
              <span className={`pp-score-pill pp-medal-${medal(player.rank)}`}>
                {player.rank}등 {Math.round(player.score)}
              </span>
            )}
          </span>
          <KdaText kda={player.kda} />
        </div>
        <div className='fighter-damage'>
          <Hatch
            percent={(player.damage / maxDamage) * PERCENT}
            from={side === 'left' ? 'right' : 'left'}
          />
          <b>{player.damage.toLocaleString('ko-KR')}</b>
        </div>
      </div>
    </div>
  );
}

function Board({ rankAt }: { rankAt: 'portrait' | 'name' }) {
  const mine = PLAYERS.filter((player) => player.team === 'mine');
  const theirs = PLAYERS.filter((player) => player.team === 'theirs');

  return (
    <ol className='board-rows'>
      {mine.map((player, index) => (
        <li key={player.championId} className='board-row'>
          <Fighter player={player} side='left' rankAt={rankAt} />
          <Fighter player={theirs[index]} side='right' rankAt={rankAt} />
        </li>
      ))}
    </ol>
  );
}

/** 내 플레이 칸에 DF 등수를 한 칸 더한다. 세 시안 모두 같이 쓴다. */
function MyPlayWithRank() {
  return (
    <section className='my-play pp-my-play'>
      <div className='pp-my-rank'>
        <strong>
          {ME.rank}
          <small>등</small>
        </strong>
        <span>DF {ME.score.toFixed(1)}</span>
      </div>
      <dl className='my-stats'>
        <div>
          <dt>킬 관여</dt>
          <dd>
            73<small>%</small>
          </dd>
        </div>
        <div>
          <dt>가한 피해</dt>
          <dd>27,640</dd>
          <dd className='my-pill'>팀 내 2위</dd>
        </div>
        <div>
          <dt>분당 CS</dt>
          <dd>8.4</dd>
          <dd className='my-pill'>총 231개</dd>
        </div>
      </dl>
    </section>
  );
}

/** A안: 챔피언 그림 모서리에 등수 딱지를 붙인다. 지금 '최다 딜' 띠와 같은 자리다. */
export function DetailA() {
  return (
    <div className='detail'>
      <MyPlayWithRank />
      <Board rankAt='portrait' />
    </div>
  );
}

/** B안: 이름 옆에 등수와 점수를 같이 적는다. 숫자를 읽고 싶은 사람에게 맞다. */
export function DetailB() {
  return (
    <div className='detail'>
      <MyPlayWithRank />
      <Board rankAt='name' />
    </div>
  );
}

/** C안: 보기 전환에 'DF 순위'를 더한다. 고르면 10명을 등수대로 한 줄씩 세운다. */
export function DetailC() {
  const [view, setView] = useState<'damage' | 'rank'>('rank');

  return (
    <div className='detail'>
      <MyPlayWithRank />
      <section className='board'>
        <div className='board-head'>
          <div className='board-views' role='tablist'>
            <button
              type='button'
              role='tab'
              aria-selected={view === 'damage'}
              onClick={() => setView('damage')}
            >
              피해량
            </button>
            <button type='button' role='tab' aria-selected={false}>
              아이템
            </button>
            <button
              type='button'
              role='tab'
              aria-selected={view === 'rank'}
              onClick={() => setView('rank')}
            >
              DF 순위
            </button>
          </div>
        </div>
        {view === 'damage' ? (
          <Board rankAt='portrait' />
        ) : (
          <ol className='pp-ranking'>
            {byRank.map((player) => (
              <li
                key={player.championId}
                className={`pp-ranking-row pp-team-${player.team} ${player.isMe ? 'pp-me' : ''}`}
              >
                <b className={`pp-ranking-no pp-medal-${medal(player.rank)}`}>{player.rank}</b>
                <img src={championIcon(player.championId)} alt='' />
                <span className='pp-ranking-name'>{player.name}</span>
                <span className='pp-ranking-bar'>
                  <Hatch percent={player.score} from='left' />
                </span>
                <span className='pp-ranking-score'>{player.score.toFixed(1)}</span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}
