import { contextBridge, ipcRenderer } from 'electron';

import type { Lineup, LcuStatus } from './types';

/**
 * 렌더러에 노출하는 창구. 메인 프로세스의 Node 권한은 넘기지 않고
 * 필요한 함수만 공개한다. 구독 함수는 해제 함수를 돌려준다.
 */
const lcuApi = {
  getLineup: (): Promise<Lineup | null> => ipcRenderer.invoke('lcu:getLineup'),

  /** 구독을 걸기 전에 이미 정해진 상태를 놓치지 않도록 직접 조회한다. */
  getStatus: (): Promise<LcuStatus> => ipcRenderer.invoke('lcu:getStatus'),

  onLineup: (callback: (lineup: Lineup | null) => void) => {
    const handler = (_event: unknown, lineup: Lineup | null) => callback(lineup);
    ipcRenderer.on('lcu:lineup', handler);
    return () => {
      ipcRenderer.removeListener('lcu:lineup', handler);
    };
  },

  onStatus: (callback: (status: LcuStatus) => void) => {
    const handler = (_event: unknown, status: LcuStatus) => callback(status);
    ipcRenderer.on('lcu:status', handler);
    return () => {
      ipcRenderer.removeListener('lcu:status', handler);
    };
  },

  /**
   * 백엔드에 추천을 요청한다. 메인 프로세스가 대신 호출하므로
   * 패키징된 앱에서도 CORS·CSP에 막히지 않는다.
   */
  recommend: <T>(body: unknown): Promise<T> => ipcRenderer.invoke('api:recommend', body),

  /** 오버레이 표시·크기 조절. 오버레이는 클릭 통과라 메인 창에서만 조작한다. */
  overlay: {
    getState: (): Promise<{ scale: number; visible: boolean }> =>
      ipcRenderer.invoke('overlay:getState'),
    setVisible: (visible: boolean): Promise<boolean> =>
      ipcRenderer.invoke('overlay:setVisible', visible),
    setScale: (scale: number): Promise<number> => ipcRenderer.invoke('overlay:setScale', scale),
    /** 단축키로 바뀐 상태를 메인 창이 따라가게 한다. */
    onState: (callback: (state: { scale: number; visible: boolean }) => void) => {
      const handler = (_event: unknown, state: { scale: number; visible: boolean }) =>
        callback(state);
      ipcRenderer.on('overlay:state', handler);
      return () => {
        ipcRenderer.removeListener('overlay:state', handler);
      };
    },
  },

  /** 롤 창 모드. 0=전체 화면, 1=테두리 없음, 2=창 모드. 모르면 null. */
  onWindowMode: (callback: (mode: number | null) => void) => {
    const handler = (_event: unknown, mode: number | null) => callback(mode);
    ipcRenderer.on('lcu:windowMode', handler);
    return () => {
      ipcRenderer.removeListener('lcu:windowMode', handler);
    };
  },
};

export type LcuApi = typeof lcuApi;

contextBridge.exposeInMainWorld('lcu', lcuApi);
