import './MatchSection.css';

import { useEffect, useState } from 'react';

import type {
  MatchDetail,
  MatchParticipant,
  MatchStats,
  MatchSummary,
  MatchTeam,
} from '../../../../shared/types';

// 챔피언은 숫자 id 로 바로 받을 수 있어 key → id 매핑표가 필요 없다.
const championIconUrl = (championId: number) =>
  `https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons/${championId}.png`;

// 아이템은 CommunityDragon 에 숫자 id 경로가 없어 DDragon 을 쓴다. 그래서 패치 버전이 필요하다.
const itemIconUrl = (version: string, itemId: number) =>
  `https://ddragon.leagueoflegends.com/cdn/${version}/img/item/${itemId}.png`;

/** DDragon 은 경로에 패치 버전이 들어간다. 목록의 첫 항목이 최신이다. */
function useDdragonVersion() {
  const [version, setVersion] = useState<string | null>(null);

  useEffect(() => {
    fetch('https://ddragon.leagueoflegends.com/api/versions.json')
      .then((response) => response.json())
      .then((versions: string[]) => setVersion(versions[0]))
      .catch(() => console.error('DDragon 버전을 불러오지 못했습니다.'));
  }, []);

  return version;
}

const formatDuration = (seconds: number) =>
  `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;

/** 좁은 칸에 만 단위를 그대로 두면 자릿수가 칸을 흔든다. */
const formatK = (value: number) =>
  value >= 1000 ? `${(value / 1000).toFixed(1)}k` : String(value);

/** 최근 판은 '3시간 전', 오래된 판은 날짜로 보여준다. */
function formatWhen(iso: string) {
  const played = new Date(iso);
  const diff = Date.now() - played.getTime();
  const hour = 60 * 60 * 1000;
  const day = 24 * hour;

  if (diff < hour) return `${Math.max(1, Math.floor(diff / (60 * 1000)))}분 전`;
  if (diff < day) return `${Math.floor(diff / hour)}시간 전`;
  if (diff < 30 * day) return `${Math.floor(diff / day)}일 전`;

  return played.toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric' });
}

/** 데스가 0이면 나눌 수 없다. 롤 클라이언트와 같이 'Perfect' 로 본다. */
const kdaValue = ({ kills, deaths, assists }: MatchStats) =>
  deaths === 0 ? Infinity : (kills + assists) / deaths;

/**
 * 평점을 색으로 나눈다. 좁은 폭에서는 숫자 크기로만 강약을 주기 어려워
 * 색이 10명을 훑을 때 눈을 멈추게 하는 유일한 장치가 된다.
 */
function kdaGrade(ratio: number) {
  if (ratio === Infinity || ratio >= 5) return 'kda-best';
  if (ratio >= 3) return 'kda-good';
  if (ratio < 1.5) return 'kda-bad';
  return '';
}

const formatRatio = (ratio: number) => (ratio === Infinity ? 'P' : ratio.toFixed(2));

/** 분당 CS. 게임이 짧으면 의미가 없어 0으로 접는다. */
const csPerMin = (cs: number, seconds: number) =>
  seconds < 60 ? '0.0' : (cs / (seconds / 60)).toFixed(1);

/** 아이템 7칸. 안 산 칸은 0 이라 빈 네모로 자리만 잡아 줄이 흔들리지 않게 한다. */
function ItemRow({ items, version }: { items: number[]; version: string | null }) {
  return (
    <span className='items'>
      {items.map((itemId, index) =>
        itemId && version ? (
          <img key={index} className='item-slot' src={itemIconUrl(version, itemId)} alt='' />
        ) : (
          <span key={index} className='item-slot item-slot-empty' />
        ),
      )}
    </span>
  );
}

/** KDA 와 평점을 한 덩어리로 묶어 2단으로 쌓는다. 좁은 폭에서 가로로 늘어놓을 자리가 없다. */
function KdaBlock({ stats, size }: { stats: MatchStats; size: 'big' | 'small' }) {
  const ratio = kdaValue(stats);

  return (
    <span className={`kda kda-${size}`}>
      <span className='kda-line'>
        {stats.kills} <span className='slash'>/</span>
        <span className='deaths'> {stats.deaths} </span>
        <span className='slash'>/</span> {stats.assists}
      </span>
      <span className={`kda-ratio ${kdaGrade(ratio)}`}>{formatRatio(ratio)}</span>
    </span>
  );
}

/**
 * 두 팀의 합계를 막대 하나로 겹쳐 보여 준다.
 * 팀마다 '51킬 · 78.6k 골드' 를 따로 쓰면 두 숫자를 눈으로 빼야 한다.
 */
function CompareBar({ label, mine, theirs }: { label: string; mine: number; theirs: number }) {
  const total = mine + theirs;
  const percent = total > 0 ? (mine / total) * 100 : 50;

  return (
    <div className='compare'>
      <span className='compare-name'>{label}</span>
      <span className='compare-bar'>
        <span className='compare-mine' style={{ width: `${percent}%` }} />
      </span>
      <span className='compare-values'>
        <b>{formatK(mine)}</b> : <b>{formatK(theirs)}</b>
      </span>
    </div>
  );
}

function RosterRow({
  participant,
  isMe,
  maxDealt,
  duration,
  version,
}: {
  participant: MatchParticipant;
  isMe: boolean;
  maxDealt: number;
  duration: number;
  version: string | null;
}) {
  const { stats } = participant;
  const dealtPercent = maxDealt > 0 ? Math.round((stats.damageDealt / maxDealt) * 100) : 0;

  return (
    <li className={`roster-row ${isMe ? 'roster-row-me' : ''}`}>
      <img className='roster-champion' src={championIconUrl(participant.championId)} alt='' />

      {/* 레벨은 아이콘 위에 겹치지 않게 제 칸을 쓴다 */}
      <span className='roster-level'>{stats.champLevel}</span>

      <span className='roster-name' title={`${participant.gameName}#${participant.tagLine}`}>
        {participant.gameName}
      </span>

      <KdaBlock stats={stats} size='small' />

      {/* 피해량은 숫자 아래 막대를 깔아 세로로 비교되게 한다 */}
      <span
        className='roster-damage'
        title={`가한 피해 ${stats.damageDealt.toLocaleString('ko-KR')}`}
      >
        <span className='roster-damage-value'>{formatK(stats.damageDealt)}</span>
        <span className='roster-damage-track'>
          <span className='roster-damage-fill' style={{ width: `${dealtPercent}%` }} />
        </span>
      </span>

      <span className='roster-cs'>
        <span className='roster-cs-value'>{stats.cs}</span>
        <span className='roster-sub'>{csPerMin(stats.cs, duration)}/분</span>
      </span>

      <ItemRow items={stats.items} version={version} />
    </li>
  );
}

function TeamBlock({
  team,
  isMyTeam,
  members,
  maxDealt,
  duration,
  myParticipantId,
  version,
}: {
  team: MatchTeam;
  isMyTeam: boolean;
  members: MatchParticipant[];
  maxDealt: number;
  duration: number;
  myParticipantId: number;
  version: string | null;
}) {
  return (
    <section className={`roster-team ${team.win ? 'roster-team-win' : 'roster-team-lose'}`}>
      <h3 className='roster-title'>
        <span className='roster-result'>{team.win ? '승리' : '패배'}</span>
        <span className='roster-side'>{isMyTeam ? '우리 팀' : '상대 팀'}</span>
      </h3>

      <ul className='roster-list'>
        {members.map((participant) => (
          <RosterRow
            key={participant.participantId}
            participant={participant}
            isMe={participant.participantId === myParticipantId}
            maxDealt={maxDealt}
            duration={duration}
            version={version}
          />
        ))}
      </ul>
    </section>
  );
}

/** 펼쳤을 때 보이는 10명. 우리 팀을 위에 둔다. */
function MatchRoster({
  detail,
  summary,
  version,
}: {
  detail: MatchDetail;
  summary: MatchSummary;
  version: string | null;
}) {
  const me = detail.participants.find(
    (participant) => participant.participantId === summary.participantId,
  );
  const myTeamId = me?.teamId ?? detail.teams[0]?.teamId ?? 100;

  // 막대는 경기 전체에서 가장 높은 값을 기준으로 삼아야 팀끼리도 비교된다.
  const maxDealt = Math.max(...detail.participants.map((player) => player.stats.damageDealt), 1);

  // 우리 팀이 먼저, 나머지가 뒤. 아레나처럼 팀이 여럿이어도 순서만 밀린다.
  const teams = [...detail.teams].sort(
    (a, b) => Number(b.teamId === myTeamId) - Number(a.teamId === myTeamId),
  );

  const membersOf = (teamId: number) =>
    detail.participants.filter((participant) => participant.teamId === teamId);
  const sumOf = (teamId: number, pick: (stats: MatchStats) => number) =>
    membersOf(teamId).reduce((total, member) => total + pick(member.stats), 0);

  const otherTeamId = teams[1]?.teamId ?? myTeamId;

  return (
    <div className='roster'>
      <div className='compare-group'>
        <CompareBar
          label='킬'
          mine={sumOf(myTeamId, (stats) => stats.kills)}
          theirs={sumOf(otherTeamId, (stats) => stats.kills)}
        />
        <CompareBar
          label='골드'
          mine={sumOf(myTeamId, (stats) => stats.goldEarned)}
          theirs={sumOf(otherTeamId, (stats) => stats.goldEarned)}
        />
      </div>

      {teams.map((team) => (
        <TeamBlock
          key={team.teamId}
          team={team}
          isMyTeam={team.teamId === myTeamId}
          members={membersOf(team.teamId)}
          maxDealt={maxDealt}
          duration={summary.gameDuration}
          myParticipantId={summary.participantId}
          version={version}
        />
      ))}
    </div>
  );
}

function MatchCard({ match, version }: { match: MatchSummary; version: string | null }) {
  const [expanded, setExpanded] = useState(false);
  const [detail, setDetail] = useState<MatchDetail | null>(null);
  const [failed, setFailed] = useState(false);

  const { stats } = match;

  const toggle = () => {
    const next = !expanded;
    setExpanded(next);

    // 펼칠 때 한 번만 받아 둔다. 다시 접었다 펴면 요청이 나가지 않는다.
    if (!next || detail) return;

    window.lcu
      .getMatchDetail(match.gameId)
      .then(setDetail)
      .catch(() => setFailed(true));
  };

  return (
    <li className={`match-card ${match.win ? 'match-card-win' : 'match-card-lose'}`}>
      <button type='button' className='match-head' onClick={toggle} aria-expanded={expanded}>
        {/* 레벨을 아이콘 아래에 둔다. 겹쳐 얹으면 챔피언이 가려진다. */}
        <span className='match-portrait'>
          <img className='match-champion' src={championIconUrl(match.championId)} alt='' />
          <span className='match-level'>Lv {stats.champLevel}</span>
        </span>

        <span className='match-info'>
          {/* 사용자 설정 게임은 큐 이름이 22자라 카드에 안 들어간다 */}
          <span className='match-queue'>{match.isCustom ? '사용자 설정' : match.queueName}</span>
          {/* 승패를 시간과 한 줄로 합쳐 카드 높이를 한 단 줄인다 */}
          <span className='match-when'>
            <b className='match-result'>{match.win ? '승리' : '패배'}</b> ·{' '}
            {formatWhen(match.gameCreationDate)} · {formatDuration(match.gameDuration)}
          </span>
        </span>

        <ItemRow items={stats.items} version={version} />

        {/* KDA 와 지표를 오른쪽 한 덩어리로 모아 빈 칸을 없앤다 */}
        <span className='match-side'>
          <KdaBlock stats={stats} size='big' />

          <span className='match-stats'>
            <span className='match-stat'>
              <i>딜</i>
              {formatK(stats.damageDealt)}
            </span>
            <span className='match-stat'>
              <i>탱</i>
              {formatK(stats.damageTaken)}
            </span>
            <span className='match-stat'>
              <i>CS</i>
              {stats.cs}
              <span className='match-sub'>({csPerMin(stats.cs, match.gameDuration)})</span>
            </span>
            <span className='match-stat'>
              <i>골드</i>
              {formatK(stats.goldEarned)}
            </span>
          </span>
        </span>

        <span className='match-arrow' aria-hidden='true'>
          {expanded ? '' : ''}
        </span>
      </button>

      {expanded &&
        (detail ? (
          <MatchRoster detail={detail} summary={match} version={version} />
        ) : (
          <p className='roster-empty'>{failed ? '기록을 가져오지 못했습니다' : '불러오는 중...'}</p>
        ))}
    </li>
  );
}

function MatchSection({ matches }: { matches: MatchSummary[] | null }) {
  const version = useDdragonVersion();

  return (
    <section className='matches'>
      <div className='matches-head'>
        <h1 className='matches-title'>전적</h1>
        {matches && <span className='matches-count'>최근 {matches.length}판</span>}
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
