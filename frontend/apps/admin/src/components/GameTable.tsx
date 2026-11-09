import type { ChampionInfo } from '../../../../packages/shared/ddragon';
import { formatDateTime, formatDuration } from '../format';
import type { PlayerGame } from '../types';

const POSITION: Record<string, string> = {
  TOP: '탑',
  JUNGLE: '정글',
  MIDDLE: '미드',
  MID: '미드',
  BOTTOM: '원딜',
  UTILITY: '서폿',
  SUPPORT: '서폿',
};

// gameTracker.ts 의 result 값
const RESULT: Record<string, { label: string; className: string }> = {
  win: { label: '승리', className: 'bg-win/15 text-win' },
  lose: { label: '패배', className: 'bg-loss/15 text-loss' },
  unknown: { label: '결과 없음', className: 'bg-surface-2 text-ink-2' },
  crash: { label: '앱 강제 종료', className: 'bg-mine/15 text-mine' },
  'app-quit': { label: '게임 중 앱 종료', className: 'bg-mine/15 text-mine' },
  // 구버전 앱: 챔피언·승패 없이 추천 이벤트로 추정한 판
  legacy: { label: '구버전 앱', className: 'bg-surface-2 text-ink-3' },
};

export function ResultBadge({ result }: { result: string }) {
  const { label, className } = RESULT[result] ?? {
    label: result,
    className: 'bg-surface-2 text-ink-2',
  };
  return (
    <span className={`rounded px-1.5 py-0.5 text-xs font-semibold whitespace-nowrap ${className}`}>
      {label}
    </span>
  );
}

export function Champion({
  name,
  champions,
}: {
  name: string | null;
  champions: Map<string, ChampionInfo>;
}) {
  if (!name) return <span className='text-ink-3'>—</span>;
  const info = champions.get(name);
  return (
    <span className='flex items-center gap-2 whitespace-nowrap'>
      {info ? (
        <img src={info.imageUrl} alt='' className='h-7 w-7 rounded' loading='lazy' />
      ) : (
        <span className='h-7 w-7 rounded bg-surface-2' />
      )}
      <span className='text-ink'>{info?.name ?? name}</span>
    </span>
  );
}

const ratio = (part: number, whole: number) => (whole > 0 ? `${part}/${whole}` : '—');

interface GameTableProps {
  games: (PlayerGame & { riotId?: string | null })[];
  champions: Map<string, ChampionInfo>;
  showPlayer?: boolean;
  onPlayerClick?: (riotId: string) => void;
}

export default function GameTable({ games, champions, showPlayer, onPlayerClick }: GameTableProps) {
  if (games.length === 0) {
    return <p className='py-4 text-center text-sm text-ink-3'>이 기간에 기록된 게임이 없습니다.</p>;
  }

  return (
    <div className='overflow-x-auto'>
      <table className='tabular w-full min-w-[880px] text-sm'>
        <thead className='text-xs text-ink-3'>
          <tr className='text-left'>
            <th className='py-2 pr-3 font-medium'>끝난 시각</th>
            {showPlayer && <th className='py-2 pr-3 font-medium'>사용자</th>}
            <th className='py-2 pr-3 font-medium'>챔피언</th>
            <th className='py-2 pr-3 font-medium'>포지션</th>
            <th className='py-2 pr-3 font-medium'>큐</th>
            <th className='py-2 pr-3 font-medium'>결과</th>
            <th
              className='py-2 pr-3 text-right font-medium'
              title='오버레이가 떠 있던 시간 (펼침 / 접힘)'
            >
              오버레이
            </th>
            <th
              className='py-2 pr-3 text-right font-medium'
              title='산 아이템 중 직전 추천 목록에 있던 것 / 전체 구매'
            >
              추천대로 구매
            </th>
            <th className='py-2 pr-3 text-right font-medium' title='추천 1순위를 그대로 산 횟수'>
              1순위
            </th>
            <th className='py-2 pr-3 text-right font-medium' title='인게임 추천 요청 성공 / 실패'>
              추천 요청
            </th>
            <th
              className='py-2 pr-3 text-right font-medium'
              title='라이브 클라이언트 API 오류 횟수'
            >
              라이브 오류
            </th>
            <th className='py-2 font-medium'>버전</th>
          </tr>
        </thead>
        <tbody>
          {games.map((game) => (
            <tr key={game.id} className='border-t border-line/60 align-middle'>
              <td className='py-2 pr-3 whitespace-nowrap text-ink-2'>
                {formatDateTime(Date.parse(game.at))}
              </td>
              {showPlayer && (
                <td className='py-2 pr-3 whitespace-nowrap'>
                  {game.riotId ? (
                    <button
                      type='button'
                      onClick={() => onPlayerClick?.(game.riotId!)}
                      className='text-ink hover:text-sky hover:underline'
                    >
                      {game.riotId}
                    </button>
                  ) : (
                    <span className='text-ink-3'>Riot ID 없음</span>
                  )}
                </td>
              )}
              <td className='py-2 pr-3'>
                <Champion name={game.champion} champions={champions} />
              </td>
              <td className='py-2 pr-3 whitespace-nowrap text-ink-2'>
                {game.position ? (POSITION[game.position] ?? game.position) : '—'}
              </td>
              <td className='py-2 pr-3 whitespace-nowrap text-ink-2'>{game.queue ?? '—'}</td>
              <td className='py-2 pr-3'>
                <ResultBadge result={game.result} />
              </td>
              <td className='py-2 pr-3 text-right whitespace-nowrap'>
                <span className='text-ink'>{formatDuration(game.overlaySec)}</span>
                {game.overlaySec > 0 && (
                  <span className='block text-xs text-ink-3'>
                    {formatDuration(game.overlayExpandedSec)} /{' '}
                    {formatDuration(game.overlayCollapsedSec)}
                  </span>
                )}
              </td>
              <td className='py-2 pr-3 text-right text-ink'>
                {ratio(game.followed, game.purchases)}
              </td>
              <td className='py-2 pr-3 text-right text-ink'>
                {ratio(game.followedTop1, game.purchases)}
              </td>
              <td className='py-2 pr-3 text-right whitespace-nowrap'>
                <span className='text-ink'>{game.recommendSuccess}</span>
                <span className='text-ink-3'> / </span>
                <span className={game.recommendError > 0 ? 'text-loss' : 'text-ink-3'}>
                  {game.recommendError}
                </span>
              </td>
              <td
                className={`py-2 pr-3 text-right ${game.liveError > 0 ? 'text-loss' : 'text-ink-3'}`}
              >
                {game.liveError}
              </td>
              <td
                className='py-2 text-xs whitespace-nowrap text-ink-3'
                title={game.gameId ?? undefined}
              >
                {game.version ?? '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
