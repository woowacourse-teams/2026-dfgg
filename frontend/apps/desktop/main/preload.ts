import { contextBridge, ipcRenderer, IpcRendererEvent } from 'electron';
import { GameflowPhase, LcuStatus, RecommendedItem } from '../types';

contextBridge.exposeInMainWorld('lcu', {
  currentSummoner: () => ipcRenderer.invoke('lcu:current-summoner'),

  getState: () => ipcRenderer.invoke('lcu:get-state'),

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

  onItemsRecommendationChange: (callback: (items: RecommendedItem[]) => void) => {
    const listener = (_: IpcRendererEvent, items: RecommendedItem[]) => callback(items);
    ipcRenderer.on('lcu:items-recommendation', listener);
    return () => ipcRenderer.removeListener('lcu:items-recommendation', listener);
  },
});
