import { app, BrowserWindow, globalShortcut, ipcMain, Menu, screen, shell } from 'electron';
import path from 'node:path';

import { trackEvent } from './analytics';
import { requestRecommendation, requestRecommendationV3 } from './backend';
import { parseChampSelect } from './champ-select';
import { type ClientWindowRect, watchClientWindow } from './client-window';
import { fetchLiveGame } from './live-client';
import {
  fetchInGame,
  fetchWindowMode,
  findCredentials,
  type LcuCredentials,
  LcuNotRunningError,
  lcuRequest,
  subscribeChampSelect,
} from './lcu';
import { createGameOverlay } from './overlay';
import { setupAutoUpdate, stopAutoUpdate } from './updater';
import type { LcuStatus, Lineup } from './types';

const isDev = !app.isPackaged;

/** 앱을 얼마나 켜두는지 보려고 남겨둔 시각. before-quit에서 이걸로 세션 길이를 잰다. */
const appLaunchedAt = Date.now();

const DEV_SERVER = 'http://localhost:3000';

/** 클라이언트가 꺼져 있을 때 다시 붙어보는 간격. */
const RECONNECT_MS = 5000;

/** 오버레이 수동 토글. 밴픽 중이 아니어도 확인할 수 있게 둔다. */
const TOGGLE_OVERLAY = 'Alt+D';

/**
 * 배율 1.0 기준 크기. 실제 크기는 overlayScale 을 곱한다.
 *
 * 폭은 아이템 6개가 한 줄에 정확히 들어가도록 맞췄다. ItemBuild 의 compact 는
 * 아이콘 w-8(32px) 에 gap-1(4px) 이므로 6*32 + 5*4 = 212. 여기에 카드 안쪽
 * 여백 p-2.5(10*2) 와 바깥 여백 p-2(8*2) 를 더해 248, 반올림 여유로 252.
 * ItemBuild 의 크기를 바꾸면 이 값도 같이 고쳐야 한다.
 *
 * 높이는 builds가 최대 3개까지 세로로 쌓이는 걸 기준으로 잡는다. 오버레이는
 * 스크롤을 못 받는 클릭-통과 창이라, 창 자체가 낮으면 두 번째·세 번째 빌드는
 * 그려지긴 해도 창 바깥이라 아예 안 보인다. 빌드 하나당 대략 65px(라벨+아이콘
 * 한 줄+카드 여백) + 빌드 사이 간격 6px 이고, 여기에 헤더 줄과 바깥 여백을
 * 더하면 3빌드 기준 약 270px. 여유를 두고 300으로 잡는다.
 */
const OVERLAY_SIZE = { width: 252, height: 300 };

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
 * 창 버튼 영역의 색. 렌더러 배경(bg-neutral-950)과 같은 값이어야 이음매가
 * 보이지 않는다. height 는 렌더러의 드래그 영역 높이(h-10)와 맞춰야 한다.
 */
const TITLE_BAR = { color: '#0a0a0a', symbolColor: '#d4d4d4', height: 40 };

/** 롤 클라이언트 옆에 붙었을 때 원하는 메인 창 폭. 자리가 좁으면 줄인다. */
const DOCK_WIDTH = 460;

/** 이보다 좁아지면 내용이 깨져서 읽을 수 없다. 반대편을 볼지 판단하는 기준. */
const MIN_DOCK_WIDTH = 320;

/**
 * 클라이언트 좌표가 이 안에서만 흔들리면 다시 붙이지 않는다. GetWindowRect가
 * 픽셀 단위로 미세하게(1~2px) 흔들리는 순간이 있어서, 문자열 비교로만 걸러내면
 * 그때마다 다시 붙어서 창이 계속 떨리듯 따라다니는 것처럼 보인다.
 */
const DOCK_JITTER_PX = 3;

/**
 * 조회가 몇 번 연속 실패해야 "끝났다"로 볼지.
 * 인게임 API는 로딩 화면이나 순간적인 부하에서 응답을 거르는 일이 있어,
 * 한 번 실패했다고 화면을 비우면 아이템이 깜빡인다.
 */
const MISS_TOLERANCE = 4;

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

/**
 * 이번 판에서 한 번이라도 본 아이템 id를 계속 들고 있는다.
 *
 * 신발 미션이 완료돼서 "미션 칸"으로 넘어가면, Riot Live Client API가 그
 * 아이템을 items 배열에서 아예 빼버린다(슬롯만 바뀌는 게 아니라 자체를 안
 * 준다). 매 폴링 스냅샷을 그대로 쓰면 그 순간 "구매 안 함"으로 되돌아가
 * 버리므로, 한 번 본 건 세션이 끝날 때까지 계속 가진 것으로 취급한다.
 */
let seenItemIds = new Set<number>();
let seenItemsSessionId = -1;

let overlayScale = 1;
let overlayVisible = true;

/** 1번/2번 추천 방식 중 화면에 보여줄 것. 메인 창에서 바꾸면 오버레이도 따라간다. */
let recommendMode: 1 | 2 = 1;

let unwatchClient: (() => void) | null = null;
/** 마지막으로 실제로 옮겨 붙인 우리 창의 위치·크기. 흔들림 판단 기준이 된다. */
let lastDockedBounds: { x: number; y: number; width: number; height: number } | null = null;
/** 직전 dock 시도가 자리 부족이었는지. 계속 좁은 동안 이벤트를 반복하지 않는다. */
let wasDockInsufficient = false;

/** 앱 안에서 열어주는 외부 주소. 여기 없는 곳으로는 보내지 않는다. */
const EXTERNAL_ORIGINS = ['https://dfgg.pro', 'https://www.dfgg.pro'];

/**
 * 링크를 기본 브라우저로 넘긴다.
 *
 * 앱 창에서 그대로 열리면 롤 조합을 보여주던 화면이 웹페이지로 덮여버리고
 * 뒤로 갈 수단도 없다. 허용한 주소만 밖으로 보내고 나머지는 막는다.
 */
function openExternally(url: string): boolean {
  if (!EXTERNAL_ORIGINS.some((origin) => url.startsWith(`${origin}/`) || url === origin)) {
    console.warn('[app] 허용하지 않은 외부 주소 차단', url);
    return false;
  }
  void shell.openExternal(url);
  return true;
}

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

/** 메인 창의 umami 로 이벤트를 흘려보낸다. */
function track(event: string, data?: Record<string, unknown>) {
  trackEvent(() => mainWindow, event, data);
}

function setStatus(status: LcuStatus) {
  if (status === lastStatus) return;
  lastStatus = status;
  console.log('[lcu] 상태', status);
  broadcast('lcu:status', status);
}

function publish(rawLineup: Lineup | null) {
  let lineup = rawLineup;
  if (lineup) {
    // 세션이 바뀌면 누적을 새로 시작한다. 지난 판 아이템이 다음 판까지 넘어가면 안 된다.
    if (lineup.sessionId !== seenItemsSessionId) {
      seenItemsSessionId = lineup.sessionId;
      seenItemIds = new Set();
    }
    for (const id of lineup.myItemIds) seenItemIds.add(id);
    lineup = { ...lineup, myItemIds: [...seenItemIds] };
  }

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

/**
 * 판이 시작됐음을 기록한다. 이미 진행 중인 판이면 번호를 그대로 둔다.
 *
 * 예외가 하나 있다. 인게임까지 갔다가 다시 밴픽이 잡히면 그건 무조건 다음
 * 판이다. 판 종료 신호를 놓친 사이(폴링 간격 안에 재큐가 잡히는 경우)에 새
 * 밴픽이 시작되면 세션 번호가 그대로 남아, 렌더러가 직전 판의 아이템을
 * 계속 보여주게 된다.
 */
function beginSession(source: Lineup['source'], reason: string) {
  const backToChampSelect = source === 'champ-select' && lastLineup?.source === 'in-game';
  if (sessionActive && !backToChampSelect) return;
  sessionActive = true;
  missCount = 0;
  sessionId += 1;
  console.log(`[lcu] 새 판 시작(${reason}) — 세션`, sessionId);
  track('session-detected', { source });
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

    // 연결 상태는 여기서 판단한다. HTTP 가 응답하면 클라이언트는 살아 있다.
    // WebSocket 이 끊겼다고 "기다리는 중"으로 표시하면, 조합은 멀쩡히 읽히는데
    // 화면에는 연결이 끊긴 것처럼 보인다.
    if (mode !== null || inGame !== null) {
      setStatus('connected');
    } else {
      // 두 호출이 모두 실패했다. 클라이언트가 꺼졌거나 포트가 바뀌었다.
      console.log('[lcu] 클라이언트 응답 없음 — 자격증명을 다시 찾는다');
      if (lastStatus !== 'disconnected') track('lcu-failed', { reason: 'lost-connection' });
      credentials = null;
      setStatus('disconnected');
      scheduleReconnect();
    }
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
    track('session-detected', { source: 'in-game' });
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
    beginSession(parsed.source, '조합 감지');
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

/**
 * 구독을 다시 걸도록 예약한다.
 *
 * credentials 는 건드리지 않는다. WebSocket 이 끊겨도 HTTP 로 조합을 읽는
 * 폴링은 계속 동작해야 한다. 자격증명 무효화는 HTTP 까지 죽었을 때만 한다.
 */
function scheduleReconnect() {
  unsubscribe?.();
  unsubscribe = null;
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

    track('lcu-connected');
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
        beginSession(parsed.source, '밴픽 이벤트');
        publish({ ...parsed, sessionId });
      },
      // 밴픽이 끝나도 판은 계속된다(로딩 → 인게임). 세션을 여기서 끊지 않고
      // gameflow 단계가 끝났다고 알려줄 때만 정리한다.
      onChampSelectEnd: () => console.log('[lcu] 밴픽 종료 — 세션 유지, 인게임 대기'),
      // 소켓이 끊겨도 폴링은 살아 있다. 상태를 내리지 말고 구독만 다시 건다.
      onDisconnect: () => {
        console.log('[lcu] WebSocket 끊김 — 폴링 유지하고 재구독 예약');
        scheduleReconnect();
      },
    });
  } catch (error) {
    const reason = error instanceof LcuNotRunningError ? 'not-running' : 'error';
    if (reason === 'error') console.error(error);
    // 클라이언트가 꺼져 있는 동안 5초마다 재시도되므로, 상태가 바뀔 때만 보낸다.
    // 매번 보내면 "롤 안 켠 사용자"가 실패 이벤트를 계속 만들어내 노이즈가 된다.
    if (lastStatus !== 'disconnected') track('lcu-failed', { reason });
    // 자격증명 자체를 못 찾았다. 이때만 연결이 없는 것으로 본다.
    credentials = null;
    setStatus('disconnected');
    scheduleReconnect();
  }
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1000,
    height: 720,
    backgroundColor: TITLE_BAR.color,
    // 제목 표시줄을 숨기고 창 버튼만 앱 배경색으로 얹는다. frame:false 로
    // 직접 그리면 최소화·최대화·스냅 동작까지 다시 만들어야 하는데, 이 방식은
    // 네이티브 동작을 그대로 두고 색만 맞춘다.
    titleBarStyle: 'hidden',
    titleBarOverlay: TITLE_BAR,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  loadDocument(mainWindow, 'main-window.html');
  // 개발 중에도 자동으로 열지 않는다. 클라이언트 옆에 붙이면 창이 좁아서
  // DevTools 가 콘텐츠를 절반 넘게 밀어낸다. 필요할 때만 켠다.
  //   $env:DFGG_DEVTOOLS = "1"
  if (isDev && process.env.DFGG_DEVTOOLS === '1') mainWindow.webContents.openDevTools();

  // target="_blank" 링크. 새 창을 만들지 않고 기본 브라우저로 넘긴다.
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    openExternally(url);
    return { action: 'deny' };
  });

  // 같은 창에서 이동하려는 시도도 막는다. 렌더러 문서는 그대로 있어야 한다.
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (url === mainWindow?.webContents.getURL()) return;
    event.preventDefault();
    openExternally(url);
  });

  // 메뉴를 없애면 거기 딸려 있던 F12·Ctrl+R 도 같이 사라진다.
  // 개발 중에는 DevTools 를 열고 새로고침할 수단이 있어야 한다.
  if (isDev) {
    mainWindow.webContents.on('before-input-event', (_event, input) => {
      if (input.type !== 'keyDown') return;
      if (input.key === 'F12') mainWindow?.webContents.toggleDevTools();
      else if (input.control && input.key.toLowerCase() === 'r') mainWindow?.webContents.reload();
    });
  }

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
 * 롤 클라이언트 오른쪽에 메인 창을 붙인다. 폴링마다(0.5초) 불리지만,
 * 실제로 자리가 바뀔 때만 setBounds를 호출한다 — 매번 강제로 다시 붙이면
 * 사용자가 창을 옮겨도 곧바로 되돌아가 버려서 직접 배치할 수가 없고,
 * GetWindowRect가 픽셀 단위로 미세하게 흔들리는 순간까지 그대로 반영하면
 * 창이 계속 떨리듯 따라다니는 것처럼 보인다.
 */
function dockToClient(rect: ClientWindowRect) {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  // 최소화된 동안 따라가면 화면 밖 좌표로 끌려간다.
  if (rect.minimized) return;

  // Win32 는 물리 픽셀을, Electron 은 DIP 를 쓴다. 배율 125% 모니터에서
  // 변환을 빼먹으면 창이 엉뚱한 자리에 놓인다.
  const client = screen.screenToDipRect(mainWindow, {
    x: rect.x,
    y: rect.y,
    width: rect.width,
    height: rect.height,
  });

  const area = screen.getDisplayMatching(client).workArea;
  const height = Math.min(client.height, area.height);

  // 클라이언트 좌우로 남는 자리를 재고, 넓은 쪽을 쓴다. 오른쪽이 기본이지만
  // 거기가 너무 좁으면 창이 쓸모없어지므로 왼쪽으로 넘긴다.
  const rightSpace = area.x + area.width - (client.x + client.width);
  const leftSpace = client.x - area.x;
  const useRight = rightSpace >= MIN_DOCK_WIDTH || rightSpace >= leftSpace;
  const space = useRight ? rightSpace : leftSpace;

  // 남는 자리에 맞춰 폭을 줄인다. 클라이언트를 덮지 않는 게 우선이다.
  // 양쪽 다 최소 폭보다 좁으면(클라이언트가 화면을 거의 채운 경우) 겹침을
  // 피할 수 없으므로, 최소한 읽을 수 있는 폭은 확보한다.
  const width = Math.min(DOCK_WIDTH, Math.max(space, MIN_DOCK_WIDTH));
  const x = useRight ? client.x + client.width : client.x - width;

  const y = Math.max(area.y, Math.min(client.y, area.y + area.height - height));

  // 우리 창이 실제로 옮겨질 계산 결과가 직전과 거의 같으면(흔들림) 그대로 둔다.
  const moved =
    !lastDockedBounds ||
    Math.abs(x - lastDockedBounds.x) > DOCK_JITTER_PX ||
    Math.abs(y - lastDockedBounds.y) > DOCK_JITTER_PX ||
    Math.abs(width - lastDockedBounds.width) > DOCK_JITTER_PX ||
    Math.abs(height - lastDockedBounds.height) > DOCK_JITTER_PX;

  if (moved) {
    mainWindow.setBounds({ x, y, width, height });
    lastDockedBounds = { x, y, width, height };

    const insufficient = space < MIN_DOCK_WIDTH;
    console.log(
      `[dock] 클라이언트 DIP ${client.width}x${client.height}@${client.x},${client.y}` +
        ` / 여백 좌${leftSpace} 우${rightSpace}` +
        ` → ${useRight ? '오른쪽' : '왼쪽'} ${width}x${height}@${x},${y}` +
        (insufficient ? ' (자리 부족 — 일부 겹침)' : ''),
    );
    // 자리 부족 상태가 새로 시작될 때만 보낸다. 창을 옮길 때마다 계속 좁으면
    // dockToClient가 매번 불려도 한 번만 기록한다.
    if (insufficient && !wasDockInsufficient) track('dock-space-insufficient');
    wasDockInsufficient = insufficient;
  }

  // 클라이언트를 클릭하면 그 창이 위로 올라오면서 우리 창을 덮는다. 옆에 붙어
  // 있으려면 같이 따라 올라와야 한다. 포커스는 뺏지 않는다. 위치가 그대로여도
  // z-order는 매번 다시 확인해야 클라이언트를 다시 눌렀을 때도 따라 올라온다.
  if (!mainWindow.isMinimized()) {
    mainWindow.showInactive();
    mainWindow.moveTop();
  }
}

function startClientDock() {
  unwatchClient?.();
  unwatchClient = watchClientWindow(
    (rect) => {
      if (!rect) {
        // 클라이언트가 꺼졌다. 다시 켜지면 그때 처음부터 다시 붙인다.
        lastDockedBounds = null;
        return;
      }
      dockToClient(rect);
    },
    () => track('window-watch-spawn-failed'),
  );
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

function setOverlayVisible(visible: boolean, source: 'hotkey' | 'button' = 'button') {
  overlayVisible = visible;
  track('overlay-toggle', { visible, source });
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

  const inGame = await createGameOverlay(
    {
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
    },
    () => mainWindow,
  );

  const created = inGame ?? createPlainOverlayWindow();
  overlayWindow = created;

  // 일반 창으로 떨어졌을 때만 최상위 다툼을 해야 한다.
  if (!inGame) startTopmostGuard();

  loadDocument(created, 'overlay.html');

  created.once('ready-to-show', () => {
    applyOverlayBounds();
    if (overlayVisible) created.showInactive();
    console.log('[overlay] 표시됨', inGame ? '(게임 내 오버레이)' : '(일반 창)');
    // 오버레이는 메인 창과 별개 렌더러라 콘솔도 따로 뜬다. detach로 열어야
    // 작고 항상 위에 떠 있는 오버레이 창 안에 끼어들지 않는다.
    if (isDev && process.env.DFGG_DEVTOOLS === '1') {
      created.webContents.openDevTools({ mode: 'detach' });
    }
  });

  created.webContents.on('did-finish-load', () => sendCurrentState(created));

  created.webContents.on('did-fail-load', (_event, code, description, url) => {
    console.error('[overlay] 로드 실패', code, description, url);
    track('overlay-load-failed', { code, description });
  });

  created.on('closed', () => {
    overlayWindow = null;
  });
}

ipcMain.handle('lcu:getLineup', () => lastLineup);

// 창이 뜨기 전에 보낸 상태 이벤트는 놓친다. 렌더러가 직접 당겨갈 수 있게 둔다.
ipcMain.handle('lcu:getStatus', () => lastStatus);

// 렌더러 대신 여기서 백엔드를 호출한다. 패키징된 앱의 CORS·CSP 제약을 피한다.
ipcMain.handle('api:recommend', (_event, body: unknown) => requestRecommendation(body));
ipcMain.handle('api:recommendV3', (_event, body: unknown) => requestRecommendationV3(body));

// 오버레이는 버튼을 못 다니 메인 창에서 바꾸면 여기서 오버레이까지 같이 알린다.
ipcMain.handle('recommend:getMode', () => recommendMode);
ipcMain.handle('recommend:setMode', (_event, mode: 1 | 2) => {
  recommendMode = mode;
  track('recommend-mode-change', { mode });
  broadcast('recommend:mode', recommendMode);
  return recommendMode;
});

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
  track('overlay-scale', { scale: overlayScale });
  applyOverlayBounds();
  return overlayScale;
});

app.whenReady().then(() => {
  // 기본 메뉴(File/Edit/View/Window)를 없앤다. 쓰는 항목이 없는데 세로 공간만
  // 잡아먹고, 클라이언트 옆에 좁게 붙이면 더 거슬린다.
  Menu.setApplicationMenu(null);

  createMainWindow();
  void createOverlayWindow();
  void connectLcu();
  // 롤 클라이언트가 이미 떠 있으면 첫 좌표가 오는 즉시 옆에 붙는다.
  startClientDock();

  // 밴픽이든 인게임이든 상태를 계속 따라잡는다. 앱을 중간에 켜도 바로 붙는다.
  pollTimer = setInterval(() => void refreshSession(), POLL_MS);
  void refreshSession();

  // 두 창 모두에 상태를 보낸다. 메인 창이 닫혀 있어도 오버레이는 살아 있다.
  setupAutoUpdate(() => [mainWindow, overlayWindow].filter((w): w is BrowserWindow => w !== null));

  const registered = globalShortcut.register(TOGGLE_OVERLAY, () => {
    if (!overlayWindow || overlayWindow.isDestroyed()) return;
    setOverlayVisible(!overlayWindow.isVisible(), 'hotkey');
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
  // 메인 창이 이미 닫혀 있으면(오버레이만 떠 있다 꺼지는 경우) 이 이벤트는
  // 유실된다 — analytics.ts와 같은 이유로 재시도하지 않는다.
  const minutes = Math.round((Date.now() - appLaunchedAt) / 60_000);
  track('desktop-app-session', { minutes });

  stopAutoUpdate();
  unsubscribe?.();
  // 감시용 PowerShell 이 남으면 앱을 꺼도 프로세스가 계속 돈다.
  unwatchClient?.();
  unwatchClient = null;
  stopPolling();
  if (topmostTimer) clearInterval(topmostTimer);
  if (reconnectTimer) clearTimeout(reconnectTimer);
  globalShortcut.unregisterAll();

  // 오버레이는 skipTaskbar 라 사용자가 직접 닫을 수단이 없다.
  // close 가 아니라 destroy 로 확실히 내린다. 남으면 유령 창이 된다.
  if (overlayWindow && !overlayWindow.isDestroyed()) overlayWindow.destroy();
  overlayWindow = null;
});
