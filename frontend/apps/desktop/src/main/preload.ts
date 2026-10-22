import { contextBridge, ipcRenderer, IpcRendererEvent } from 'electron';
import {
  EndedGame,
  FeedbackRating,
  GameflowPhase,
  HighlightProgress,
  LcuStatus,
  OverlayClickTarget,
  RecommendationUpdate,
  Summoner,
} from '../shared/types';
import type { Locale } from '../shared/i18n';

contextBridge.exposeInMainWorld('lcu', {
  currentSummoner: () => ipcRenderer.invoke('lcu:current-summoner'),

  getRankInfo: () => ipcRenderer.invoke('lcu:summoner-rank-info'),

  getProfileBackground: () => ipcRenderer.invoke('lcu:profile-background'),

  launchClient: () => ipcRenderer.invoke('lcu:launch-client'),

  getState: () => ipcRenderer.invoke('lcu:get-state'),

  getMatchHistoryInfo: () => ipcRenderer.invoke('lcu:match-history'),

  getMatchDetail: (gameId: number) => ipcRenderer.invoke('lcu:match-detail', gameId),

  getDfScores: (gameId: number) => ipcRenderer.invoke('lcu:df-score', gameId),

  onStatusChange: (callback: (status: LcuStatus) => void) => {
    const listener = (_: IpcRendererEvent, status: LcuStatus) => callback(status);
    ipcRenderer.on('lcu:status', listener);
    return () => ipcRenderer.removeListener('lcu:status', listener);
  },

  onSummonerChange: (callback: (summoner: Summoner) => void) => {
    const listener = (_: IpcRendererEvent, summoner: Summoner) => callback(summoner);
    ipcRenderer.on('lcu:summoner', listener);
    return () => ipcRenderer.removeListener('lcu:summoner', listener);
  },

  onProfileBackgroundChange: (callback: (skinId: number | null) => void) => {
    const listener = (_: IpcRendererEvent, skinId: number | null) => callback(skinId);
    ipcRenderer.on('lcu:profile-background', listener);
    return () => ipcRenderer.removeListener('lcu:profile-background', listener);
  },

  onPhaseChange: (callback: (phase: GameflowPhase) => void) => {
    const listener = (_: IpcRendererEvent, phase: GameflowPhase) => callback(phase);
    ipcRenderer.on('lcu:phase', listener);
    return () => ipcRenderer.removeListener('lcu:phase', listener);
  },

  onItemsRecommendationChange: (callback: (items: RecommendationUpdate[]) => void) => {
    const listener = (_: IpcRendererEvent, items: RecommendationUpdate[]) => callback(items);
    ipcRenderer.on('lcu:items-recommendation', listener);
    return () => ipcRenderer.removeListener('lcu:items-recommendation', listener);
  },

  getEndedGame: () => ipcRenderer.invoke('lcu:ended-game'),
});

contextBridge.exposeInMainWorld('windowControls', {
  setCollapsed: (collapsed: boolean) => ipcRenderer.send('window:set-collapsed', collapsed),
  setOverlayHeight: (height: number) => ipcRenderer.send('window:set-overlay-height', height),
  minimize: () => ipcRenderer.send('window:minimize'),
  close: () => ipcRenderer.send('window:close'),
});

contextBridge.exposeInMainWorld('analytics', {
  track: (name: string, data?: Record<string, string | number | boolean>) =>
    ipcRenderer.send('analytics:track', name, data),
  overlayClick: (target: OverlayClickTarget) => ipcRenderer.send('overlay:click', target),
});

contextBridge.exposeInMainWorld('feedback', {
  submit: (rating: FeedbackRating, game: EndedGame) =>
    ipcRenderer.invoke('feedback:submit', rating, game),
});

contextBridge.exposeInMainWorld('highlight', {
  list: () => ipcRenderer.invoke('highlight:list'),

  summary: (gameIds: number[]) => ipcRenderer.invoke('highlight:summary', gameIds),

  get: (gameId: number) => ipcRenderer.invoke('highlight:get', gameId),

  onProgress: (callback: (progress: HighlightProgress) => void) => {
    const listener = (_: IpcRendererEvent, progress: HighlightProgress) => callback(progress);
    ipcRenderer.on('highlight:progress', listener);
    return () => ipcRenderer.removeListener('highlight:progress', listener);
  },
});

contextBridge.exposeInMainWorld('appLocale', ipcRenderer.sendSync('locale:get'));

contextBridge.exposeInMainWorld('settings', {
  setLocale: (locale: Locale) => ipcRenderer.invoke('locale:set', locale),
  getAutoLaunch: () => ipcRenderer.invoke('autoLaunch:get'),
  setAutoLaunch: (enabled: boolean) => ipcRenderer.invoke('autoLaunch:set', enabled),
});
