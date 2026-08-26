import type { LcuApi } from '../electron/preload';

declare global {
  interface Window {
    /** preload가 contextBridge로 노출한다. Electron 밖(브라우저)에서는 없다. */
    lcu?: LcuApi;
    /** index.html의 umami 스크립트가 붙여준다. */
    umami?: {
      track: (event: string, data?: Record<string, unknown>) => void;
    };
  }
}

export {};
