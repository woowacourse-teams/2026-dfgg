import { BrowserWindow } from 'electron';
import type { GameflowPhase } from '../../shared/types';
import { isOverlayQueue } from '../lcu/queue';
import { getLcuState } from '../lcu/state';

let overlayWindow: BrowserWindow | null = null;
let enabled = true;
let inGame = false;

function apply() {
  if (!overlayWindow || overlayWindow.isDestroyed()) return;
  if (enabled && inGame) overlayWindow.showInactive();
  else overlayWindow.hide();
}

export function initOverlayVisibility(win: BrowserWindow, initialEnabled: boolean) {
  overlayWindow = win;
  enabled = initialEnabled;
  apply();
}

export async function updateOverlayPhase(phase: GameflowPhase | null) {
  const show = phase === 'InProgress' && (await isOverlayQueue());
  inGame = show && getLcuState().phase === 'InProgress';
  apply();
}

export function setOverlayEnabled(value: boolean) {
  enabled = value;
  apply();
}

export const isOverlayEnabled = () => enabled;
