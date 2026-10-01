import type { BrowserWindow, BrowserWindowConstructorOptions } from 'electron';
import { app } from 'electron';

import { trackEvent } from './analytics';

/** Overwolf 게임 ID. 타입 패키지의 game-list 에서 확인한 값이다. */
export const LEAGUE_OF_LEGENDS_GAME_ID = 5426;

interface GameLaunchEvent {
  /** 이걸 불러야 실제로 주입된다. 등록만으로는 오버레이가 뜨지 않는다. */
  inject(): void;
}

interface GameInfo {
  supported?: boolean;
  name?: string;
  id?: number;
}

/**
 * ow-electron 오버레이 API 중 우리가 쓰는 부분만 좁게 선언한다.
 * 전체 타입은 @overwolf/ow-electron-packages-types 에 있지만, 일반 electron 으로
 * 실행할 때도 컴파일이 되어야 하므로 여기서 최소한만 정의한다.
 */
interface OverlayApi {
  registerGames(filter: { gamesIds?: number[]; includeUnsupported?: boolean }): void;
  createWindow(
    options: BrowserWindowConstructorOptions & { name: string },
  ): Promise<{ window: BrowserWindow }>;
  on(event: string, listener: (...args: never[]) => void): unknown;
}

interface OwPackages {
  overlay?: OverlayApi;
  on(event: 'ready', listener: (event: unknown, packageName: string) => void): void;
}

interface OwApp {
  overwolf?: { packages?: OwPackages };
}

function getPackages(): OwPackages | undefined {
  return (app as unknown as OwApp).overwolf?.packages;
}

/** 지금 실행 중인 런타임이 ow-electron 인지. 일반 electron 이면 false. */
export function isOwElectron(): boolean {
  return Boolean(getPackages());
}

/** 오버레이 패키지가 준비될 때까지 기다린다. 없으면 null. */
function waitForOverlayApi(timeoutMs = 10_000): Promise<OverlayApi | null> {
  const packages = getPackages();
  if (!packages) return Promise.resolve(null);
  if (packages.overlay) return Promise.resolve(packages.overlay);

  return new Promise((resolve) => {
    const timer = setTimeout(() => {
      console.error('[overlay] 패키지 준비 시간 초과');
      resolve(null);
    }, timeoutMs);

    packages.on('ready', (_event, packageName) => {
      if (packageName !== 'overlay') return;
      clearTimeout(timer);
      resolve(packages.overlay ?? null);
    });
  });
}

/**
 * 게임 안에 그려지는 오버레이 창을 만든다.
 *
 * 일반 BrowserWindow 와 달리 게임 프로세스 안에서 렌더링되므로 전체 화면에서도
 * 가려지지 않는다. ow-electron 이 아니거나 오버레이 패키지가 없으면 null 을
 * 돌려주고, 호출한 쪽이 기존 방식으로 떨어진다.
 *
 * getMainWindow 는 fallback 사유를 렌더러에 흘려보내기 위한 것뿐이라 없어도
 * 동작에는 영향이 없다(메인 창이 아직 안 뜬 시점에 호출될 수 있어 선택값이다).
 */
export async function createGameOverlay(
  options: BrowserWindowConstructorOptions & { name: string },
  getMainWindow?: () => BrowserWindow | null,
): Promise<BrowserWindow | null> {
  const fallback = (reason: string) => {
    if (getMainWindow) trackEvent(getMainWindow, 'game-overlay-fallback', { reason });
  };

  if (!getPackages()) {
    console.log('[overlay] ow-electron 이 아님 — 일반 창으로 대체');
    fallback('not-ow-electron');
    return null;
  }

  const api = await waitForOverlayApi();
  if (!api) {
    console.error('[overlay] 오버레이 패키지를 쓸 수 없음 — 일반 창으로 대체');
    fallback('api-unavailable');
    return null;
  }

  // 게임이 뜨면 알림이 오는데, 등록만으로는 주입되지 않는다.
  // 반드시 event.inject() 를 직접 불러야 게임 안에 그려진다.
  api.on('game-launched', ((event: GameLaunchEvent, gameInfo: GameInfo) => {
    console.log(
      '[overlay] 게임 감지',
      gameInfo?.name ?? gameInfo?.id,
      '지원:',
      gameInfo?.supported,
    );
    if (gameInfo?.supported) {
      event.inject();
      console.log('[overlay] 주입 요청');
    }
  }) as (...args: never[]) => void);

  api.on('game-injection-error', ((gameInfo: GameInfo, error: unknown) => {
    console.error('[overlay] 주입 실패', gameInfo?.name, error);
    fallback('inject-error');
  }) as (...args: never[]) => void);

  api.on('game-injected', ((gameInfo: GameInfo) => {
    console.log('[overlay] 주입 성공', gameInfo?.name);
  }) as (...args: never[]) => void);

  api.on('game-exit', ((gameInfo: GameInfo) => {
    console.log('[overlay] 게임 종료', gameInfo?.name);
  }) as (...args: never[]) => void);

  // 등록은 주입 대상 게임을 알려주는 것까지만 한다.
  api.registerGames({ gamesIds: [LEAGUE_OF_LEGENDS_GAME_ID] });

  try {
    const created = await api.createWindow(options);
    console.log('[overlay] 게임 내 오버레이 창 생성됨');
    return created.window;
  } catch (error) {
    console.error('[overlay] 창 생성 실패 — 일반 창으로 대체', error);
    fallback('create-window-failed');
    return null;
  }
}
