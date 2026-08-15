import type { DDragonData } from '../../../../packages/shared/ddragon';
import type { Position } from '../../../../packages/shared/types';
import ItemBuild from '../../components/ItemBuild';
import OverlayControls from '../../components/OverlayControls';
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

const STATUS_LABEL = {
  connected: '롤 클라이언트 연결됨',
  disconnected: '롤 클라이언트를 기다리는 중',
  unavailable: '데스크톱 앱에서만 동작해요',
};

function ChampionRow({ slots, ddragon }: { slots: LineupSlot[]; ddragon: DDragonData | null }) {
  return (
    <ul className="flex gap-2">
      {slots.map((slot, index) => {
        const champion = ddragon && findChampion(ddragon, slot.championId, slot.championName);
        return (
          <li key={slot.cellId >= 0 ? slot.cellId : index} className="w-16 text-center">
            {champion ? (
              <img src={champion.imageUrl} alt={champion.name} width={56} height={56} />
            ) : (
              <div className="size-14 rounded bg-neutral-800" />
            )}
            <p className="mt-1 truncate text-[11px] text-neutral-400">{champion?.name ?? '미정'}</p>
          </li>
        );
      })}
    </ul>
  );
}

export default function App() {
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
    <div className="min-h-dvh bg-neutral-950 p-6 text-neutral-100">
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <h1 className="text-xl font-bold">밴픽 아이템 추천</h1>
        <p className="text-sm text-neutral-400">{STATUS_LABEL[status]}</p>
      </header>

      <OverlayControls />

      {windowMode === WINDOW_MODE_FULLSCREEN && (
        <div className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-300">
          <p>
            롤이 <strong>전체 화면</strong>이라 오버레이가 게임에 가려집니다.
          </p>
          <button
            type="button"
            onClick={() => void window.lcu?.setBorderless()}
            className="shrink-0 cursor-pointer rounded bg-amber-400 px-3 py-1.5 text-xs font-bold text-amber-950 hover:bg-amber-300"
          >
            테두리 없음으로 바꾸기
          </button>
        </div>
      )}

      {!lineup && (
        <p className="mt-8 text-sm text-neutral-400">
          챔피언 선택이나 게임이 시작되면 조합을 자동으로 읽어옵니다.
        </p>
      )}

      {lineup && (
        <>
          <section className="mt-6 space-y-4">
            <div>
              <h2 className="mb-1.5 text-xs font-bold tracking-wider text-emerald-400">
                아군 {allyPicked} / 5
              </h2>
              <ChampionRow slots={lineup.allies} ddragon={ddragon} />
            </div>
            <div>
              <h2 className="mb-1.5 text-xs font-bold tracking-wider text-rose-400">
                상대 {enemyPicked} / 5
              </h2>
              <ChampionRow slots={lineup.enemies} ddragon={ddragon} />
            </div>
          </section>

          {!request && (
            <p className="mt-6 text-sm text-neutral-400">
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

      {loading && <p className="mt-6 text-sm text-neutral-400">분석 중...</p>}

      {error && (
        <p className="mt-6 text-sm text-rose-400" role="alert">
          {error}
        </p>
      )}

      {result && ddragon && !loading && (
        <section className="mt-8" aria-live="polite">
          <h2 className="text-lg font-bold">
            {(lineup && findChampion(ddragon, lineup.myChampionId, lineup.myChampionName)?.name) ??
              result.champion}
            <span className="ml-2 text-sm font-normal text-neutral-400">
              {POSITION_LABEL[result.position] ?? result.position}
            </span>
          </h2>
          <div className="mt-3">
            <ItemBuild items={result.items} ddragon={ddragon} />
          </div>
        </section>
      )}
    </div>
  );
}
