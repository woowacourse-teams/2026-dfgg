import type {
  PhaseListener,
  GameflowPhase,
  LcuState,
  LcuStatus,
  RecommendedItem,
} from '../../shared/types';
import { broadcastToAllWindows } from '../ipc/broadcast';

const state: LcuState = {
  status: 'disconnected',
  phase: null,
  recommendations: null,
};
const phaseListeners = new Set<PhaseListener>();

// phase 구독자 담기
export function onPhaseChange(listener: PhaseListener) {
  phaseListeners.add(listener);
  return () => phaseListeners.delete(listener);
}

export function getLcuState(): Readonly<LcuState> {
  return state;
}

export function setLcuPhase(phase: GameflowPhase | null) {
  if (state.phase === phase) return;
  state.phase = phase;
  broadcastToAllWindows('lcu:phase', phase);

  if (phase !== 'InProgress') setRecommendations(null);

  for (const listener of phaseListeners) listener(phase);
}

export function setLcuStatus(status: LcuStatus) {
  if (state.status === status) return;
  state.status = status;
  console.log('LCU 상태', status);
  broadcastToAllWindows('lcu:status', status);

  if (status === 'disconnected') setLcuPhase(null);
}

export function setRecommendations(result: RecommendedItem[] | null) {
  state.recommendations = result;
  broadcastToAllWindows('lcu:items-recommendation', result);
}
