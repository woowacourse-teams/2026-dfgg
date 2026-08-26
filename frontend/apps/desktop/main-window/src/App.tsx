import type { DDragonData } from '../../../../packages/shared/ddragon';
import type { Position } from '../../../../packages/shared/types';
import BuildList from '../../components/BuildList';
import OverlayControls from '../../components/OverlayControls';
import UpdateBanner from '../../components/UpdateBanner';
import { useAnalyticsBridge } from '../../components/useAnalyticsBridge';
import { WINDOW_MODE_FULLSCREEN } from '../../components/useLineup';
import { findChampion, isPicked, useRecommendation } from '../../components/useRecommendation';
import type { LineupSlot } from '../../electron/types';

const POSITION_LABEL: Record<Position, string> = {
  TOP: '탑',
  JUNGLE: '정글',
  MID: '미드',
  BOTTOM: '원딜',
  SUPPORT: '서폿',
};

/** 메인 프로세스가 기본 브라우저로 열어준다. 앱 창 안에서는 열리지 않는다. */
const PRIVACY_URL = 'https://dfgg.pro/privacy';

/** 서비스를 처음 공개한 해. 해가 바뀌어도 그대로 둔다. */
const COPYRIGHT_YEAR = 2026;

const STATUS_LABEL = {
  connected: '롤 클라이언트 연결됨',
  disconnected: '롤 클라이언트를 기다리는 중',
  unavailable: '데스크톱 앱에서만 동작해요',
};

function ChampionRow({ slots, ddragon }: { slots: LineupSlot[]; ddragon: DDragonData | null }) {
  return (
    <ul className='flex gap-2'>
      {slots.map((slot, index) => {
        const champion = ddragon && findChampion(ddragon, slot.championId, slot.championName);
        return (
          <li key={slot.cellId >= 0 ? slot.cellId : index} className='w-16 text-center'>
            {champion ? (
              <img src={champion.imageUrl} alt={champion.name} width={56} height={56} />
            ) : (
              <div className='size-14 rounded bg-neutral-800' />
            )}
            <p className='mt-1 truncate text-[11px] text-neutral-400'>{champion?.name ?? '미정'}</p>
          </li>
        );
      })}
    </ul>
  );
}

export default function App() {
  useAnalyticsBridge();

  const {
    lineup,
    status,
    windowMode,
    ddragon,
    request,
    result,
    error,
    loading,
    allyPicked,
    enemyPicked,
  } = useRecommendation();

  return (
    <div className='min-h-dvh bg-neutral-950 text-neutral-100'>
      {/*
        제목 표시줄을 숨긴 자리. 창 버튼은 오른쪽에 네이티브로 얹히므로 여기서는
        끌 수 있는 영역만 만들어 준다. 높이는 메인 프로세스의 TITLE_BAR.height 와
        같아야 버튼과 어긋나지 않는다.
      */}
      <div className='flex h-10 items-center px-5 [-webkit-app-region:drag]'>
        <span className='text-[11px] font-bold tracking-[0.2em] text-neutral-600 uppercase'>
          dfgg
        </span>
      </div>

      <div className='px-6 pb-6'>
        <header className='flex flex-wrap items-baseline justify-between gap-2'>
          <h1 className='text-xl font-bold'>밴픽 아이템 추천</h1>
          <p className='text-sm text-neutral-400'>{STATUS_LABEL[status]}</p>
        </header>

        <UpdateBanner />

        <OverlayControls />

        {/*
        설정은 사용자가 직접 바꾼다. 대신 눌러주는 기능은 롤 클라이언트에 쓰기가
        일어나는 동작이라 Riot 정책상 위험해 두지 않았다.
      */}
        {windowMode === WINDOW_MODE_FULLSCREEN && (
          <div className='mt-4 rounded border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-300'>
            <p>
              롤이 <strong>전체 화면</strong>이라 오버레이가 게임에 가려집니다.
            </p>
            <p className='mt-1.5 text-xs text-amber-300/80'>
              롤 설정 → 그래픽 → 창 모드를 <strong>테두리 없음</strong>이나 <strong>창 모드</strong>
              로 바꿔주세요.
            </p>
          </div>
        )}

        {!lineup && (
          <p className='mt-8 text-sm text-neutral-400'>
            챔피언 선택이나 게임이 시작되면 조합을 자동으로 읽어옵니다.
          </p>
        )}

        {lineup && (
          <>
            <section className='mt-6 space-y-4'>
              <div>
                <h2 className='mb-1.5 text-xs font-bold tracking-wider text-emerald-400'>
                  아군 {allyPicked} / 5
                </h2>
                <ChampionRow slots={lineup.allies} ddragon={ddragon} />
              </div>
              <div>
                <h2 className='mb-1.5 text-xs font-bold tracking-wider text-rose-400'>
                  상대 {enemyPicked} / 5
                </h2>
                <ChampionRow slots={lineup.enemies} ddragon={ddragon} />
              </div>
            </section>

            {!request && (
              <p className='mt-6 text-sm text-neutral-400'>
                {!lineup.myPosition && '내 포지션을 아직 알 수 없어요. '}
                {!isPicked({
                  cellId: lineup.myCellId,
                  championId: lineup.myChampionId,
                  championName: lineup.myChampionName,
                  position: null,
                }) && '내 챔피언을 고르면 '}
                {enemyPicked < 5 &&
                  '상대 챔피언이 모두 공개되면 추천을 요청합니다. 블라인드 픽에서는 게임 시작 전까지 상대가 보이지 않아요.'}
                {enemyPicked === 5 && allyPicked < 5 && '아군이 모두 확정되면 추천을 요청합니다.'}
              </p>
            )}
          </>
        )}

        {loading && <p className='mt-6 text-sm text-neutral-400'>분석 중...</p>}

        {error && (
          <p className='mt-6 text-sm text-rose-400' role='alert'>
            {error}
          </p>
        )}

        {result && result.builds.length === 0 && !loading && (
          <p className='mt-8 text-sm text-neutral-400'>이 조합엔 추천할 빌드가 아직 없어요.</p>
        )}

        {result && ddragon && !loading && result.builds.length > 0 && (
          <section className='mt-8' aria-live='polite'>
            <h2 className='text-lg font-bold'>
              {(lineup &&
                findChampion(ddragon, lineup.myChampionId, lineup.myChampionName)?.name) ??
                result.champion}
              <span className='ml-2 text-sm font-normal text-neutral-400'>
                {POSITION_LABEL[result.position] ?? result.position}
              </span>
            </h2>
            <div className='mt-3'>
              <BuildList builds={result.builds} ddragon={ddragon} ownedItemIds={lineup?.myItemIds} />
            </div>
          </section>
        )}

        <footer className='mt-10 border-t border-neutral-800 pt-4 text-xs text-neutral-500'>
          <p>
            개인정보처리방침{' '}
            {/* 눌러도 기본 브라우저로 열리니, 주소를 그대로 적어 옮겨적을 수도 있게 한다. */}
            <a
              href={PRIVACY_URL}
              target='_blank'
              rel='noreferrer'
              className='text-neutral-400 hover:text-neutral-200 hover:underline'
            >
              {PRIVACY_URL}
            </a>
          </p>
          <p className='mt-2 leading-relaxed'>
            dfgg는 Riot Games와 제휴하거나 승인받은 서비스가 아닙니다. League of Legends와 Riot
            Games는 Riot Games, Inc.의 상표입니다.
          </p>
          {/* Riot 이 요구하는 고지문 원문. 의역하지 않고 그대로 둔다. */}
          <p className='mt-1.5 leading-relaxed'>
            dfgg isn&apos;t endorsed by Riot Games and doesn&apos;t reflect the views or opinions of
            Riot Games or anyone officially involved in producing or managing Riot Games properties.
            Riot Games and all associated properties are trademarks or registered trademarks of Riot
            Games, Inc.
          </p>
          {/* 챔피언·아이템 이미지가 Riot 저작물이므로 출처를 함께 남긴다. */}
          <p className='mt-2'>© {COPYRIGHT_YEAR} dfgg. League of Legends © Riot Games, Inc.</p>
        </footer>
      </div>
    </div>
  );
}
