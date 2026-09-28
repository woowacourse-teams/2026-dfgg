import { contextBridge, ipcRenderer, IpcRendererEvent } from 'electron';
import { GameflowPhase, LcuStatus, RecommendationUpdate } from '../shared/types';

contextBridge.exposeInMainWorld('lcu', {
  currentSummoner: () => ipcRenderer.invoke('lcu:current-summoner'),

  getRankInfo: () => ipcRenderer.invoke('lcu:summoner-rank-info'),

  getState: () => ipcRenderer.invoke('lcu:get-state'),

  getMatchHistoryInfo: () => ipcRenderer.invoke('lcu:match-history'),

  getMatchDetail: (gameId: number) => ipcRenderer.invoke('lcu:match-detail', gameId),

  onStatusChange: (callback: (status: LcuStatus) => void) => {
    const listener = (_: IpcRendererEvent, status: LcuStatus) => callback(status);
    ipcRenderer.on('lcu:status', listener);
    return () => ipcRenderer.removeListener('lcu:status', listener);
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
});

contextBridge.exposeInMainWorld('windowControls', {
  setCollapsed: (collapsed: boolean) => ipcRenderer.send('window:set-collapsed', collapsed),
  minimize: () => ipcRenderer.send('window:minimize'),
  close: () => ipcRenderer.send('window:close'),
});

contextBridge.exposeInMainWorld('analytics', {
  track: (name: string, data?: Record<string, string | number | boolean>) =>
    ipcRenderer.send('analytics:track', name, data),
});
