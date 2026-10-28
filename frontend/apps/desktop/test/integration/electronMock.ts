import os from 'node:os';

/**
 * 통합 테스트용 가짜 electron. Electron 없이 도는 Vitest 에서 main 코드를 불러오기 위해 쓴다.
 * 진짜 코드가 electron 에게 묻는 것만 답한다. 새 에러가 나면 여기에 하나씩 채운다.
 */
export const electronMock = {
  app: {
    isPackaged: false,
    getLocale: () => 'ko',
    getPath: () => os.tmpdir(),
    getVersion: () => '0.0.0-test',
  },
  BrowserWindow: class {
    static getAllWindows() {
      return [];
    }
  },
  screen: {},
  desktopCapturer: { getSources: async () => [] },
};
