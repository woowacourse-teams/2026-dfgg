import type {
  PhaseListener,
  StatusListener,
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
const statusListeners = new Set<StatusListener>();

// phase 구독자 담기
export function onPhaseChange(listener: PhaseListener) {
  phaseListeners.add(listener);
  return () => phaseListeners.delete(listener);
}

// status 구독자 담기
export function onStatusChange(listener: StatusListener) {
  statusListeners.add(listener);
  return () => statusListeners.delete(listener);
}

export function getLcuState(): Readonly<LcuState> {
  return state;
}

export function setLcuPhase(phase: GameflowPhase | null) {
  if (state.phase === phase) return;
  state.phase = phase;
  broadcastToAllWindows('lcu:phase', phase);

  if (phase !== 'InProgress') setRecommendations(null);

  for (const listener of phaseListeners) {
    try {
      listener(phase);
    } catch (error) {
      console.error('phase 구독자 실행 실패', error);
    }
  }
}

export function setLcuStatus(status: LcuStatus) {
  if (state.status === status) return;
  state.status = status;
  console.log('LCU 상태', status);
  broadcastToAllWindows('lcu:status', status);

  if (status === 'disconnected') setLcuPhase(null);

  for (const listener of statusListeners) {
    try {
      listener(status);
    } catch (error) {
      console.error('status 구독자 실행 실패', error);
    }
  }
}

export function setRecommendations(result: RecommendedItem[] | null) {
  state.recommendations = result;
  broadcastToAllWindows('lcu:items-recommendation', result);
}
