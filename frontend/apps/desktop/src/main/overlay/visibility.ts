import { BrowserWindow } from 'electron';
import { GameflowPhase } from '../../shared/types';

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

export function updateOverlayPhase(phase: GameflowPhase | null) {
  inGame = phase === 'InProgress';
  apply();
}

export function setOverlayEnabled(value: boolean) {
  enabled = value;
  apply();
}

export const isOverlayEnabled = () => enabled;
