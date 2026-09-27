import './ProfileHeader.css';

import type { GameflowPhase, LcuStatus, Summoner } from '../../../../shared/types';

const STATUS_TEXT: Record<LcuStatus, string> = {
  disconnected: '롤 클라이언트 대기 중',
  connecting: '연결 중',
  connected: '연결됨',
};

const PHASE_TEXT: Record<GameflowPhase, string> = {
  None: '로비',
  Lobby: '로비',
  Matchmaking: '매칭 중',
  ReadyCheck: '수락 대기',
  ChampSelect: '밴픽 중',
  GameStart: '게임 시작',
  InProgress: '게임 중',
  WaitingForStats: '결과 집계 중',
  PreEndOfGame: '명예 평가',
  EndOfGame: '게임 종료',
  Reconnect: '재접속',
  TerminatedInError: '에러 발생',
};

const profileIconUrl = (id: number) =>
  `https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/profile-icons/${id}.jpg`;

type Props = {
  summoner: Summoner;
  status: LcuStatus | null;
  phase: GameflowPhase | null;
};

function ProfileHeader({ summoner, status, phase }: Props) {
  return (
    <header className='status-bar'>
      {/* 프로필 아이콘 위에 레벨을 겹쳐 얹는다 — 롤 클라이언트와 같은 관습 */}
      <div className='avatar'>
        <img className='avatar-image' src={profileIconUrl(summoner.profileIconId)} alt='' />
        <span className='avatar-level'>{summoner.summonerLevel}</span>
      </div>

      <div className='identity'>
        <span className='identity-name'>
          {summoner.gameName}
          <span className='identity-tag'>#{summoner.tagLine}</span>
        </span>

        {/* 다음 레벨까지의 진행도 */}
        <div
          className='xp-bar'
          role='progressbar'
          aria-valuenow={summoner.percentCompleteForNextLevel}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label='다음 레벨까지 경험치'
          title={`${summoner.xpSinceLastLevel} / ${summoner.xpUntilNextLevel} XP`}
        >
          <span className='xp-fill' style={{ width: `${summoner.percentCompleteForNextLevel}%` }} />
        </div>
      </div>

      {/* 연결됐으면 게임 단계만 보여준다. '연결됨' 과 '로비' 를 같이 띄우면 중복이다. */}
      <div className='connection' title={status ? STATUS_TEXT[status] : '확인 중'}>
        <span className={`status-dot status-dot-${status ?? 'unknown'}`} aria-hidden='true' />
        <span className='connection-text'>
          {status === 'connected'
            ? (phase && PHASE_TEXT[phase]) || '로비'
            : (status && STATUS_TEXT[status]) || '확인 중'}
        </span>
      </div>
    </header>
  );
}

export default ProfileHeader;
