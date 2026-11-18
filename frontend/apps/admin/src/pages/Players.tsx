import { Fragment, useMemo, useState } from 'react';

import { playersUrl } from '../api';
import GameTable, { Champion } from '../components/GameTable';
import KpiTile from '../components/KpiTile';
import PageMeta from '../components/PageMeta';
import Panel, { Unavailable } from '../components/Panel';
import SourceNotice, { RequestError } from '../components/SourceNotice';
import {
  formatDateTime,
  formatDuration,
  formatNumber,
  formatPercent,
  relativeTime,
} from '../format';
import { type Player, type PlayersResponse, SOURCE_NAME } from '../types';
import { useChampions } from '../useChampions';
import { useJson } from '../useJson';

type View = 'players' | 'games';
type Sort = 'recent' | 'games';

function matches(player: Player, query: string) {
  if (!query) return true;
  const q = query.toLowerCase();
  return (
    (player.riotId ?? '').toLowerCase().includes(q) ||
    player.games.some((game) => (game.champion ?? '').toLowerCase().includes(q))
  );
}

function winRate(wins: number, losses: number) {
  return wins + losses > 0 ? formatPercent(wins / (wins + losses)) : '—';
}

export default function Players({ days, reloadKey }: { days: number; reloadKey: number }) {
  const { data, error, loading } = useJson<PlayersResponse>(playersUrl(days), reloadKey);
  const champions = useChampions();
  const [view, setView] = useState<View>('players');
  const [sort, setSort] = useState<Sort>('recent');
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState<string | null>(null);

  const result = data?.result.ok ? data.result.data : null;
  const players = useMemo(() => result?.players ?? [], [result]);

  const visible = useMemo(() => {
    const list = players.filter((player) => matches(player, query.trim()));
    if (sort === 'games') return [...list].sort((a, b) => b.stats.games - a.stats.games);
    return list;
  }, [players, query, sort]);

  const allGames = useMemo(
    () =>
      visible
        .flatMap((player) => player.games.map((game) => ({ ...game, riotId: player.riotId })))
        .sort((a, b) => b.at.localeCompare(a.at)),
    [visible],
  );

  const totals = useMemo(() => {
    const sum = (key: keyof Player['stats']) =>
      players.reduce((total, player) => total + (player.stats[key] as number), 0);
    return {
      wins: sum('wins'),
      losses: sum('losses'),
      overlaySec: sum('overlaySec'),
      purchases: sum('purchases'),
      followed: sum('followed'),
    };
  }, [players]);

  const showPlayer = (riotId: string) => {
    setView('players');
    setQuery(riotId);
    setOpen(`riot:${riotId}`);
  };

  return (
    <div className='space-y-5'>
      <PageMeta generatedAt={data?.range.generatedAt} mock={data?.mock} loading={loading} />
      {error && <RequestError message={error} />}
      {data && <SourceNotice name={SOURCE_NAME[data.source]} source={data.result} />}

      {result && (
        <>
          <div className='grid grid-cols-2 gap-3 lg:grid-cols-5'>
            <KpiTile label='연결한 사용자' value={players.filter((p) => p.riotId).length} />
            <KpiTile label='기록된 게임' value={result.totals.games} />
            <KpiTile
              label='승률'
              value={
                totals.wins + totals.losses > 0 ? totals.wins / (totals.wins + totals.losses) : null
              }
              format={formatPercent}
              hint={`${totals.wins}승 ${totals.losses}패 (결과 있는 게임만)`}
            />
            <KpiTile
              label='게임당 오버레이 시간'
              value={result.totals.games > 0 ? totals.overlaySec / result.totals.games : null}
              format={formatDuration}
            />
            <KpiTile
              label='추천대로 구매'
              value={totals.purchases > 0 ? totals.followed / totals.purchases : null}
              format={formatPercent}
              hint={`구매 ${formatNumber(totals.purchases)}회 중 ${formatNumber(totals.followed)}회`}
            />
          </div>

          <Panel
            title={view === 'players' ? '사용자' : '최근 게임'}
            description='desktop-connect · desktop-game-end 이벤트에 담긴 Riot ID 기준으로 묶었습니다. 구버전 앱 사용자는 Riot ID 없이 기기 단위로, 게임은 추천 이벤트로 추정합니다.'
            action={
              <div className='flex rounded-md border border-line p-0.5' role='tablist'>
                {(
                  [
                    ['players', '사용자별'],
                    ['games', '최근 게임'],
                  ] as const
                ).map(([key, label]) => (
                  <button
                    key={key}
                    type='button'
                    role='tab'
                    aria-selected={view === key}
                    onClick={() => setView(key)}
                    className={`rounded px-3 py-1 text-xs ${
                      view === key ? 'bg-cobalt-deep text-ink' : 'text-ink-2 hover:text-ink'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>
            }
          >
            <div className='mb-3 flex flex-wrap items-center gap-2'>
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder='Riot ID 또는 챔피언 검색'
                className='min-w-48 flex-1 rounded-md border border-line bg-ground px-3 py-1.5 text-sm text-ink placeholder:text-ink-3 focus:border-cobalt focus:outline-none'
              />
              {view === 'players' && (
                <select
                  value={sort}
                  onChange={(event) => setSort(event.target.value as Sort)}
                  className='rounded-md border border-line bg-ground px-2 py-1.5 text-sm text-ink-2'
                  aria-label='정렬'
                >
                  <option value='recent'>최근 접속순</option>
                  <option value='games'>게임 많은 순</option>
                </select>
              )}
            </div>

            {view === 'games' ? (
              <GameTable
                games={allGames}
                champions={champions}
                showPlayer
                onPlayerClick={showPlayer}
              />
            ) : visible.length === 0 ? (
              <Unavailable>
                {players.length === 0
                  ? '이 기간에 앱을 연결한 사용자가 없습니다.'
                  : '검색 결과가 없습니다.'}
              </Unavailable>
            ) : (
              <div className='overflow-x-auto'>
                <table className='tabular w-full min-w-[820px] text-sm'>
                  <thead className='text-xs text-ink-3'>
                    <tr className='text-left'>
                      <th className='py-2 pr-3 font-medium'>Riot ID</th>
                      <th className='py-2 pr-3 text-right font-medium'>레벨</th>
                      <th className='py-2 pr-3 text-right font-medium'>게임</th>
                      <th className='py-2 pr-3 text-right font-medium'>승률</th>
                      <th className='py-2 pr-3 font-medium'>많이 한 챔피언</th>
                      <th className='py-2 pr-3 font-medium'>마지막 접속</th>
                      <th className='py-2 pr-3 font-medium'>지역</th>
                      <th className='py-2 font-medium'>앱 버전</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visible.map((player) => {
                      const expanded = open === player.key;
                      return (
                        <Fragment key={player.key}>
                          <tr
                            className={`cursor-pointer border-t border-line/60 hover:bg-surface-2 ${
                              expanded ? 'bg-surface-2' : ''
                            }`}
                            onClick={() => setOpen(expanded ? null : player.key)}
                          >
                            <td className='py-2.5 pr-3'>
                              <span className='mr-2 inline-block w-3 text-ink-3'>
                                {expanded ? '▾' : '▸'}
                              </span>
                              <span className='font-semibold text-ink'>
                                {player.riotId ?? (
                                  <span className='font-normal text-ink-3'>
                                    Riot ID 없음 (구버전 앱)
                                  </span>
                                )}
                              </span>
                            </td>
                            <td className='py-2.5 pr-3 text-right text-ink-2'>
                              {player.level ?? '—'}
                            </td>
                            <td className='py-2.5 pr-3 text-right text-ink'>
                              {player.stats.games}
                            </td>
                            <td className='py-2.5 pr-3 text-right whitespace-nowrap'>
                              <span className='text-ink'>
                                {winRate(player.stats.wins, player.stats.losses)}
                              </span>
                              {player.stats.wins + player.stats.losses > 0 && (
                                <span className='ml-1 text-xs text-ink-3'>
                                  {player.stats.wins}승 {player.stats.losses}패
                                </span>
                              )}
                            </td>
                            <td className='py-2.5 pr-3'>
                              <div className='flex gap-3'>
                                {player.stats.topChampions.length === 0 && (
                                  <span className='text-ink-3'>—</span>
                                )}
                                {player.stats.topChampions.map((champion) => (
                                  <span key={champion.name} className='flex items-center gap-1'>
                                    <Champion name={champion.name} champions={champions} />
                                    <span className='text-xs text-ink-3'>{champion.games}</span>
                                  </span>
                                ))}
                              </div>
                            </td>
                            <td
                              className='py-2.5 pr-3 whitespace-nowrap text-ink-2'
                              title={
                                player.lastAt
                                  ? formatDateTime(Date.parse(player.lastAt))
                                  : undefined
                              }
                            >
                              {relativeTime(player.lastAt)}
                            </td>
                            <td className='py-2.5 pr-3 whitespace-nowrap text-ink-2'>
                              {player.location ?? '—'}
                            </td>
                            <td className='py-2.5 text-xs whitespace-nowrap text-ink-3'>
                              {player.version ?? '—'}
                            </td>
                          </tr>
                          {expanded && (
                            <tr>
                              <td colSpan={8} className='bg-ground/60 px-3 pt-2 pb-4'>
                                <p className='mb-2 text-xs text-ink-3'>
                                  처음 본 날{' '}
                                  {player.firstAt
                                    ? formatDateTime(Date.parse(player.firstAt))
                                    : '—'}
                                  {' · '}클라이언트 연결 {player.connects}회{' · '}오버레이{' '}
                                  {formatDuration(player.stats.overlaySec)}
                                  {' · '}추천대로 구매 {player.stats.followed}/
                                  {player.stats.purchases}
                                  {' · '}
                                  {player.os ?? ''}
                                </p>
                                <GameTable games={player.games} champions={champions} />
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </Panel>
        </>
      )}
    </div>
  );
}
