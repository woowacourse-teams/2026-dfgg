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

const POSITIONS: Position[] = ['TOP', 'JUNGLE', 'MID', 'BOTTOM', 'SUPPORT'];

/**
 * 밴픽 중이라는 것과 지금 내 포지션·챔피언. 화면 맨 위에 한 줄로 둔다.
 * 포지션이 배정되지 않는 큐(블라인드·칼바람·사용자 설정)는 여기서 알약 탭으로 고른다.
 */
function ContextBar({ state }: { state: ChampSelectState }) {
  const { position, positionAssigned, myChampionId } = state;

  const pick = (next: Position) => {
    window.champSelect.pickPosition(next);
    window.analytics.track('champ-select-pick-position', { position: next });
  };

  return (
    <header className='cs-context'>
      <span className='cs-phase'>{t.champSelect.phase}</span>
      {myChampionId !== null && <img src={championIconUrl(myChampionId)} alt='' />}
      {positionAssigned && position ? (
        <b>{t.champSelect.positions[position]}</b>
      ) : (
        <div
          className='board-views cs-positions'
          role='tablist'
          aria-label={t.champSelect.positionTabs}
        >
          {POSITIONS.map((candidate) => (
            <button
              key={candidate}
              type='button'
              role='tab'
              aria-selected={candidate === position}
              onClick={() => pick(candidate)}
            >
              {t.champSelect.positions[candidate]}
            </button>
          ))}
        </div>
      )}
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

/** 밴픽 화면: 오늘의 픽 → 카운터 → 룬. 포지션을 알아야 추천을 받으므로 그전에는 고르라고만 한다. */
function ChampSelectSection({ state }: { state: ChampSelectState }) {
  const [selectedRank, selectRank] = useSelectedRank(state);
  const proInUse = typeof state.applied === 'string' ? state.applied.slice('pro:'.length) : null;

  const applyPro = (playerName: string) => {
    window.champSelect.applyProPage(playerName);
    window.analytics.track('champ-select-pro-runes');
  };

  return (
    <div className='cs'>
      <ContextBar state={state} />
      {!state.position && (
        <p className='csr-empty'>
          <b>{t.champSelect.choosePosition}</b>
          <small>{t.champSelect.choosePositionDesc}</small>
        </p>
      )}
      {state.todayPicks && state.todayPicks.picks.length > 0 && (
        <TodayPickHero todayPicks={state.todayPicks} proInUse={proInUse} onUsePro={applyPro} />
      )}
      {state.counters && <CounterSection counters={state.counters} locked={state.locked} />}
      {state.position && (
        <RuneSection
          myChampionId={state.myChampionId}
          runes={state.runes}
          selectedRank={selectedRank}
          proInUse={proInUse !== null}
          onSelect={selectRank}
          error={state.runeError}
        />
      )}
    </div>
  );
}

export default ChampSelectSection;
