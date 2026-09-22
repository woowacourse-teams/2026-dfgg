import { GameflowPhase } from '../../types';

export const LCU_URI = {
  gameflowPhase: '/lol-gameflow/v1/gameflow-phase',
} as const;

export type LcuEventMap = {
  [LCU_URI.gameflowPhase]: GameflowPhase;
};
