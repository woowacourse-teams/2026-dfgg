import { app, BrowserWindow, globalShortcut, ipcMain, screen } from 'electron';
import path from 'node:path';

import { requestRecommendation } from './backend';
import { parseChampSelect } from './champ-select';
import { fetchLiveGame } from './live-client';
import {
  fetchInGame,
  fetchWindowMode,
  findCredentials,
  type LcuCredentials,
  LcuNotRunningError,
  lcuRequest,
  setWindowMode,
  subscribeChampSelect,
} from './lcu';
import { createGameOverlay } from './overlay';
import type { LcuStatus, Lineup } from './types';

const isDev = !app.isPackaged;

const DEV_SERVER = 'http://localhost:3000';

/** 클라이언트가 꺼져 있을 때 다시 붙어보는 간격. */
const RECONNECT_MS = 5000;

/** 오버레이 수동 토글. 밴픽 중이 아니어도 확인할 수 있게 둔다. */
const TOGGLE_OVERLAY = 'Alt+D';

/** 배율 1.0 기준 크기. 실제 크기는 overlayScale 을 곱한다. */
const OVERLAY_SIZE = { width: 340, height: 132 };

/** 화면 가장자리에서 띄울 여백. */
const OVERLAY_MARGIN = 24;

/** 사용자가 고를 수 있는 오버레이 배율. */
const OVERLAY_SCALES = [0.8, 1, 1.25, 1.5] as const;

/**
 * WebSocket 이벤트를 놓쳐도 상태가 따라잡히도록 주기적으로 세션을 다시 읽는다.
 * 로컬 호출이라 비용이 거의 없고, 앱을 껐다 켜도 진행 중인 밴픽을 바로 잡아준다.
 */
const POLL_MS = 3000;

/** 최상위 지정을 다시 걸어주는 간격. */
const TOPMOST_GUARD_MS = 1000;

/**
 * 조회가 몇 번 연속 실패해야 "끝났다"로 볼지.
 * 인게임 API는 로딩 화면이나 순간적인 부하에서 응답을 거르는 일이 있어,
 * 한 번 실패했다고 화면을 비우면 아이템이 깜빡인다.
 */
const MISS_TOLERANCE = 4;

/** 롤 창 모드 값. 테두리 없음이어야 오버레이가 게임 위에 뜬다. */
const WINDOW_MODE_BORDERLESS = 1;

let mainWindow: BrowserWindow | null = null;
let overlayWindow: BrowserWindow | null = null;
let unsubscribe: (() => void) | null = null;
let reconnectTimer: NodeJS.Timeout | null = null;
let pollTimer: NodeJS.Timeout | null = null;
let topmostTimer: NodeJS.Timeout | null = null;
let credentials: LcuCredentials | null = null;

let lastLineup: Lineup | null = null;
let lastStatus: LcuStatus = 'disconnected';
/** 같은 내용을 반복 전송·기록하지 않으려고 직전 값을 문자열로 들고 있는다. */
let lastPublished = '';
/** 롤 창 모드. 0=전체 화면이면 오버레이가 가려진다. */
let lastWindowMode: number | null = null;
/** 연속 조회 실패 횟수. MISS_TOLERANCE 를 넘겨야 종료로 판단한다. */
let missCount = 0;

/** 한 판을 식별하는 번호. 밴픽~게임 종료까지 같은 값을 유지한다. */
let sessionId = 0;
let sessionActive = false;

let overlayScale = 1;
let overlayVisible = true;

/** 렌더러 문서를 연다. 개발 중에는 dev 서버, 배포본은 파일에서 읽는다. */
function loadDocument(window: BrowserWindow, name: string) {
  if (isDev) {
    void window.loadURL(`${DEV_SERVER}/${name}`);
  } else {
    void window.loadFile(path.join(__dirname, `../${name}`));
  }
}

/** 두 창 모두에 같은 상태를 보낸다. */
function broadcast(channel: string, payload: unknown) {
  for (const window of [mainWindow, overlayWindow]) {
    if (window && !window.isDestroyed()) window.webContents.send(channel, payload);
  }
}

function setStatus(status: LcuStatus) {
  lastStatus = status;
  broadcast('lcu:status', status);
}

function publish(lineup: Lineup | null) {
  const serialized = JSON.stringify(lineup);
  if (serialized === lastPublished) return; // 바뀐 게 없으면 조용히 넘어간다
  lastPublished = serialized;

  const picked = (slots: { championId: number }[]) =>
    slots.filter((slot) => slot.championId > 0).length;
  console.log(
    '[lcu] publish',
    lineup
      ? `아군 ${picked(lineup.allies)}/${lineup.allies.length}, 적군 ${picked(lineup.enemies)}/${lineup.enemies.length}, 내 챔피언 ${lineup.myChampionId}, 포지션 ${lineup.myPosition}`
      : 'null (밴픽 종료)',
  );

  lastLineup = lineup;
  broadcast('lcu:lineup', lineup);
}

/** 창이 새로 뜨면 지금까지의 상태를 그 창에만 다시 보낸다. */
function sendCurrentState(window: BrowserWindow) {
  if (window.isDestroyed()) return;
  window.webContents.send('lcu:status', lastStatus);
  window.webContents.send('lcu:lineup', lastLineup);
  window.webContents.send('lcu:windowMode', lastWindowMode);
}

/**
 * 현재 상태를 다시 읽는다. 밴픽이 우선이고, 밴픽이 끝났으면 진행 중인 게임을 본다.
 * 게임이 시작되면 밴픽 세션은 404가 되므로 두 곳을 모두 봐야 한다.
 */
/** 한 판이 끝났다고 확정할 때만 부른다. 다음 판은 새 sessionId 를 받는다. */
function endSession() {
  if (!sessionActive && lastLineup === null) return;
  sessionActive = false;
  missCount = 0;
  publish(null);
}

async function refreshSession() {
  let inGame: boolean | null = null;

  if (credentials) {
    const mode = await fetchWindowMode(credentials);
    if (mode !== lastWindowMode) {
      lastWindowMode = mode;
      console.log('[lcu] 창 모드', mode, '(0=전체 화면, 1=테두리 없음, 2=창 모드)');
      broadcast('lcu:windowMode', mode);
    }

    // 밴픽·로딩·인게임을 한 판으로 묶어주는 유일한 신호다.
    inGame = await fetchInGame(credentials);
  }

  // 클라이언트가 "판이 끝났다"고 알려주면 즉시 정리한다.
  if (inGame === false) {
    if (sessionActive) console.log('[lcu] 판 종료 — 세션', sessionId, '정리');
    endSession();
    return;
  }

  // 판이 시작됐다. 밴픽이든 인게임이든 여기서 같은 세션 번호를 받는다.
  if (inGame === true && !sessionActive) {
    sessionActive = true;
    sessionId += 1;
    console.log('[lcu] 새 판 시작 — 세션', sessionId);
  }

  let parsed: Lineup | null = null;

  if (credentials) {
    try {
      const session = await lcuRequest(credentials, '/lol-champ-select/v1/session');
      parsed = parseChampSelect(session);
    } catch {
      // 밴픽 중이 아니다. 아래에서 인게임을 확인한다.
    }
  }

  if (!parsed) parsed = await fetchLiveGame();

  if (parsed) {
    // 단계 조회가 실패해도(구버전·권한 등) 조합이 잡히면 판이 시작된 것으로 본다.
    if (!sessionActive) {
      sessionActive = true;
      sessionId += 1;
      console.log('[lcu] 새 판 시작(조합 감지) — 세션', sessionId);
    }
    missCount = 0;
    publish({ ...parsed, sessionId });
    return;
  }

  // 여기부터는 조합을 못 읽은 경우다.
  // 단계가 "진행 중"이면 로딩 화면일 뿐이니 직전 조합을 그대로 유지한다.
  if (inGame === true) return;

  // 단계를 아예 못 물어봤을 때만 연속 실패 횟수로 판단한다.
  if (lastLineup === null) return;

  missCount += 1;
  if (missCount < MISS_TOLERANCE) {
    console.log(`[lcu] 조회 실패 ${missCount}/${MISS_TOLERANCE} — 직전 조합 유지`);
    return;
  }

  console.log('[lcu] 연속 실패로 판 종료 처리');
  endSession();
}

function stopPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = null;
}

function scheduleReconnect() {
  unsubscribe?.();
  unsubscribe = null;
  credentials = null;
  // 폴링은 멈추지 않는다. 인게임 API는 LCU 연결과 무관하게 동작한다.
  if (reconnectTimer) return;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    void connectLcu();
  }, RECONNECT_MS);
}

async function connectLcu() {
  try {
    credentials = await findCredentials();

    setStatus('connected');
    await refreshSession();

    unsubscribe = subscribeChampSelect(credentials, {
      onChampSelect: (session) => {
        const parsed = parseChampSelect(session);
        // 형태가 예상과 다른 이벤트 하나 때문에 멀쩡한 조합이 지워지면 안 된다.
        if (!parsed) {
          console.log('[lcu] 파싱 실패 — 이전 조합 유지');
          return;
        }
        if (!sessionActive) {
          sessionActive = true;
          sessionId += 1;
          console.log('[lcu] 새 판 시작(밴픽 이벤트) — 세션', sessionId);
        }
        publish({ ...parsed, sessionId });
      },
      // 밴픽이 끝나도 판은 계속된다(로딩 → 인게임). 세션을 여기서 끊지 않고
      // gameflow 단계가 끝났다고 알려줄 때만 정리한다.
      onChampSelectEnd: () => console.log('[lcu] 밴픽 종료 — 세션 유지, 인게임 대기'),
      onDisconnect: () => {
        setStatus('disconnected');
        scheduleReconnect();
      },
    });
  } catch (error) {
    if (!(error instanceof LcuNotRunningError)) console.error(error);
    setStatus('disconnected');
    scheduleReconnect();
  }
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1000,
    height: 720,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  loadDocument(mainWindow, 'main-window.html');
  if (isDev) mainWindow.webContents.openDevTools();

  // 창이 뜨기 전에 보낸 상태는 사라지므로 로드가 끝나면 다시 보낸다.
  // HMR로 새로고침될 때도 매번 이 이벤트가 온다.
  mainWindow.webContents.on('did-finish-load', () => {
    if (mainWindow) sendCurrentState(mainWindow);
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
    // 오버레이는 별도 창이라 메인 창만 닫아도 window-all-closed 가 오지 않는다.
    // 메인 창이 이 앱의 본체이므로 닫히면 오버레이까지 같이 정리한다.
    if (process.platform !== 'darwin') app.quit();
  });
}

/**
 * 배율에 맞춰 오버레이 크기와 위치를 다시 잡는다.
 * 창 크기만 키우면 내용은 그대로라, zoomFactor 로 렌더링까지 함께 확대한다.
 */
function applyOverlayBounds() {
  if (!overlayWindow || overlayWindow.isDestroyed()) return;

  const { height: screenHeight } = screen.getPrimaryDisplay().workAreaSize;
  const width = Math.round(OVERLAY_SIZE.width * overlayScale);
  const height = Math.round(OVERLAY_SIZE.height * overlayScale);

  overlayWindow.setBounds({
    x: OVERLAY_MARGIN,
    y: Math.round((screenHeight - height) / 2),
    width,
    height,
  });
  overlayWindow.webContents.setZoomFactor(overlayScale);
}

function setOverlayVisible(visible: boolean) {
  overlayVisible = visible;
  if (!overlayWindow || overlayWindow.isDestroyed()) return;
  if (visible) overlayWindow.showInactive();
  else overlayWindow.hide();
  // 메인 창의 토글 상태를 실제와 맞춘다. 단축키로 바꿨을 때도 반영된다.
  mainWindow?.webContents.send('overlay:state', { scale: overlayScale, visible: overlayVisible });
}

/**
 * 최상위 유지. 게임이 포그라운드를 잡을 때 Windows가 topmost 지정을 해제하는 일이
 * 잦아서, 보이는 동안 주기적으로 다시 올려준다.
 */
function startTopmostGuard() {
  if (topmostTimer) clearInterval(topmostTimer);
  topmostTimer = setInterval(() => {
    if (!overlayWindow || overlayWindow.isDestroyed() || !overlayWindow.isVisible()) return;
    overlayWindow.setAlwaysOnTop(true, 'screen-saver');
    overlayWindow.moveTop();
  }, TOPMOST_GUARD_MS);
}

/** ow-electron 이 아닐 때 쓰는 일반 창. 전체 화면 게임에는 가려진다. */
function createPlainOverlayWindow(): BrowserWindow {
  const { height } = screen.getPrimaryDisplay().workAreaSize;

  const created = new BrowserWindow({
    ...OVERLAY_SIZE,
    // 화면 왼쪽 중앙. 미니맵·아이템창과 겹치지 않는 자리다.
    x: OVERLAY_MARGIN,
    y: Math.round((height - OVERLAY_SIZE.height) / 2),
    frame: false,
    transparent: true,
    resizable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    show: true,
    // 포커스를 가져가지 않는다. 게임 중에 클릭이 오버레이로 새면 안 된다.
    focusable: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  created.setAlwaysOnTop(true, 'screen-saver');
  created.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true });
  created.setIgnoreMouseEvents(true, { forward: true });

  // 게임이 포그라운드를 잡으면 Windows가 최상위 지정을 풀어버린다.
  return created;
}

async function createOverlayWindow() {
  // ow-electron 이면 게임 프로세스 안에 그린다. 전체 화면에서도 가려지지 않는다.
  const { height } = screen.getPrimaryDisplay().workAreaSize;

  const inGame = await createGameOverlay({
    ...OVERLAY_SIZE,
    name: 'dfgg-overlay',
    x: OVERLAY_MARGIN,
    y: Math.round((height - OVERLAY_SIZE.height) / 2),
    transparent: true,
    frame: false,
    resizable: false,
    show: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  const created = inGame ?? createPlainOverlayWindow();
  overlayWindow = created;

  // 일반 창으로 떨어졌을 때만 최상위 다툼을 해야 한다.
  if (!inGame) startTopmostGuard();

  loadDocument(created, 'overlay.html');

  created.once('ready-to-show', () => {
    applyOverlayBounds();
    if (overlayVisible) created.showInactive();
    console.log('[overlay] 표시됨', inGame ? '(게임 내 오버레이)' : '(일반 창)');
  });

  created.webContents.on('did-finish-load', () => sendCurrentState(created));

  created.webContents.on('did-fail-load', (_event, code, description, url) => {
    console.error('[overlay] 로드 실패', code, description, url);
  });

  created.on('closed', () => {
    overlayWindow = null;
  });
}

ipcMain.handle('lcu:getLineup', () => lastLineup);

// 렌더러 대신 여기서 백엔드를 호출한다. 패키징된 앱의 CORS·CSP 제약을 피한다.
ipcMain.handle('api:recommend', (_event, body: unknown) => requestRecommendation(body));

// 오버레이는 클릭 통과라 자기 자신에 버튼을 달 수 없다. 메인 창에서 조절한다.
ipcMain.handle('overlay:getState', () => ({ scale: overlayScale, visible: overlayVisible }));

ipcMain.handle('overlay:setVisible', (_event, visible: boolean) => {
  setOverlayVisible(visible);
  return overlayVisible;
});

ipcMain.handle('overlay:setScale', (_event, scale: number) => {
  // 임의의 값이 들어와도 화면을 뒤덮지 않도록 허용 범위로 제한한다.
  const min = OVERLAY_SCALES[0];
  const max = OVERLAY_SCALES[OVERLAY_SCALES.length - 1];
  overlayScale = Math.min(Math.max(scale, min), max);
  applyOverlayBounds();
  return overlayScale;
});

/** 사용자가 배너의 버튼을 눌렀을 때만 실행된다. 몰래 설정을 바꾸지 않는다. */
ipcMain.handle('lcu:setBorderless', async () => {
  if (!credentials) return false;
  try {
    await setWindowMode(credentials, WINDOW_MODE_BORDERLESS);
    lastWindowMode = WINDOW_MODE_BORDERLESS;
    broadcast('lcu:windowMode', WINDOW_MODE_BORDERLESS);
    console.log('[lcu] 창 모드를 테두리 없음으로 변경');
    return true;
  } catch (error) {
    console.error('[lcu] 창 모드 변경 실패', error);
    return false;
  }
});

app.whenReady().then(() => {
  createMainWindow();
  void createOverlayWindow();
  void connectLcu();

  // 밴픽이든 인게임이든 상태를 계속 따라잡는다. 앱을 중간에 켜도 바로 붙는다.
  pollTimer = setInterval(() => void refreshSession(), POLL_MS);
  void refreshSession();

  const registered = globalShortcut.register(TOGGLE_OVERLAY, () => {
    if (!overlayWindow || overlayWindow.isDestroyed()) return;
    setOverlayVisible(!overlayWindow.isVisible());
  });
  console.log(`[overlay] 단축키 ${TOGGLE_OVERLAY} 등록`, registered ? '성공' : '실패');

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createMainWindow();
      void createOverlayWindow();
    }
  });
});

// 오버레이는 숨겨진 창이라 메인 창을 닫으면 앱이 안 꺼진다. 직접 종료한다.
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('before-quit', () => {
  unsubscribe?.();
  stopPolling();
  if (topmostTimer) clearInterval(topmostTimer);
  if (reconnectTimer) clearTimeout(reconnectTimer);
  globalShortcut.unregisterAll();

  // 오버레이는 skipTaskbar 라 사용자가 직접 닫을 수단이 없다.
  // close 가 아니라 destroy 로 확실히 내린다. 남으면 유령 창이 된다.
  if (overlayWindow && !overlayWindow.isDestroyed()) overlayWindow.destroy();
  overlayWindow = null;
});
