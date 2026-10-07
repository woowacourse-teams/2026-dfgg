import './ProfileHeader.css';

import type { Summoner } from '../../../../shared/types';
import { championSplashUrl } from '../../lib/ddragon';
import { CloseIcon, MinimizeIcon } from '../icons';

const profileIconUrl = (id: number) =>
  `https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/profile-icons/${id}.jpg`;

const PERCENT = 100;
const RING_SIZE = 76;
const RING_STROKE = 3;
const RING_RADIUS = (RING_SIZE - RING_STROKE) / 2;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

type Props = {
  summoner: Summoner | null;
  /** 배너에 깔 챔피언. 최근에 가장 많이 한 챔피언을 넘긴다. */
  backdropChampionId?: number;
};

function ProfileHeader({ summoner, backdropChampionId }: Props) {
  return (
    <>
      <header className='title-bar'>
        <img className='title-bar-logo' src='./icon.png' alt='' />
        <span className='title-bar-name'>DFGG</span>

        <div className='title-bar-controls'>
          <button
            type='button'
            className='title-bar-button'
            aria-label='최소화'
            onClick={() => window.windowControls.minimize()}
          >
            <MinimizeIcon />
          </button>
          <button
            type='button'
            className='title-bar-button title-bar-close'
            aria-label='닫기'
            onClick={() => window.windowControls.close()}
          >
            <CloseIcon />
          </button>
        </div>
      </header>

      {summoner && (
        <section className='hero'>
          <div className='hero-banner'>
            {backdropChampionId !== undefined && (
              <img
                // 챔피언이 바뀌면 새 그림이 다시 서서히 나타나야 한다
                key={backdropChampionId}
                src={championSplashUrl(backdropChampionId)}
                alt=''
                onLoad={(event) => event.currentTarget.classList.add('hero-banner-loaded')}
              />
            )}
          </div>

          <div className='hero-body'>
            <div
              className='avatar'
              role='progressbar'
              aria-valuenow={summoner.percentCompleteForNextLevel}
              aria-valuemin={0}
              aria-valuemax={PERCENT}
              aria-label='다음 레벨까지 경험치'
              title={`${summoner.xpSinceLastLevel} / ${summoner.xpUntilNextLevel} XP`}
            >
              {/* 경험치는 프로필 둘레 고리로 보여 준다 */}
              <svg className='avatar-ring' viewBox={`0 0 ${RING_SIZE} ${RING_SIZE}`} aria-hidden>
                <circle className='avatar-ring-track' cx='50%' cy='50%' r={RING_RADIUS} />
                <circle
                  className='avatar-ring-fill'
                  cx='50%'
                  cy='50%'
                  r={RING_RADIUS}
                  strokeDasharray={RING_LENGTH}
                  strokeDashoffset={
                    RING_LENGTH * (1 - summoner.percentCompleteForNextLevel / PERCENT)
                  }
                />
              </svg>
              <img className='avatar-image' src={profileIconUrl(summoner.profileIconId)} alt='' />
              <span className='avatar-level'>{summoner.summonerLevel}</span>
            </div>

            <h1 className='identity'>
              {summoner.gameName}
              <span className='identity-tag'>#{summoner.tagLine}</span>
            </h1>
          </div>
        </section>
      )}
    </>
  );
}

export default ProfileHeader;
