import './ChampSelect.css';

import * as Sentry from '@sentry/electron/renderer';
import { useEffect, useState } from 'react';

import type { ChampSelectState, Position } from '../../../../shared/types';
import { t } from '../../../i18n';
import { championIconUrl } from '../../lib/ddragon';
import CounterSection from './CounterSection';
import RuneSection from './RuneSection';
import TodayPickHero from './TodayPickHero';

/** 밴픽 상태를 받아 둔다. 밴픽이 아니면 null */
export function useChampSelect(): ChampSelectState | null {
  const [state, setState] = useState<ChampSelectState | null>(null);

  useEffect(() => {
    window.champSelect
      .getState()
      .then(setState)
      .catch((error) => Sentry.captureException(error));
    return window.champSelect.onStateChange(setState);
  }, []);

  return state;
}

/** 밴픽 중이라는 것과 지금 내 포지션·챔피언. 화면 맨 위에 한 줄로 둔다. */
function ContextBar({ position, championId }: { position: Position; championId: number | null }) {
  return (
    <header className='cs-context'>
      <span className='cs-phase'>{t.champSelect.phase}</span>
      {championId !== null && <img src={championIconUrl(championId)} alt='' />}
      <b>{t.champSelect.positions[position]}</b>
    </header>
  );
}

/** 고른 페이지 순위. 챔피언이 바뀌면 1순위로 돌아간다. */
function useSelectedRank(state: ChampSelectState) {
  const [selected, setSelected] = useState({ championId: state.myChampionId, rank: 1 });
  const rank = selected.championId === state.myChampionId ? selected.rank : 1;

  const select = (next: number) => {
    setSelected({ championId: state.myChampionId, rank: next });
    window.champSelect.applyPage(next);
    window.analytics.track('champ-select-rune-page', { rank: next });
  };
  return [rank, select] as const;
}

/** 밴픽 화면: 오늘의 픽 → 카운터 → 룬. 배정 포지션이 있을 때만 그린다(App 이 거른다). */
function ChampSelectSection({ state, position }: { state: ChampSelectState; position: Position }) {
  const [selectedRank, selectRank] = useSelectedRank(state);
  const proInUse = typeof state.applied === 'string' ? state.applied.slice('pro:'.length) : null;

  const applyPro = (playerName: string) => {
    window.champSelect.applyProPage(playerName);
    window.analytics.track('champ-select-pro-runes');
  };

  return (
    <div className='cs'>
      <ContextBar position={position} championId={state.myChampionId} />
      {state.todayPicks && state.todayPicks.picks.length > 0 && (
        <TodayPickHero todayPicks={state.todayPicks} proInUse={proInUse} onUsePro={applyPro} />
      )}
      {state.counters && <CounterSection counters={state.counters} locked={state.locked} />}
      <RuneSection
        myChampionId={state.myChampionId}
        runes={state.runes}
        selectedRank={selectedRank}
        proInUse={proInUse !== null}
        onSelect={selectRank}
        error={state.runeError}
      />
    </div>
  );
}

export default ChampSelectSection;
