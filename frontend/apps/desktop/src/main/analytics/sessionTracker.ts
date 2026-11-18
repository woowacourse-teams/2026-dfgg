import type { BrowserWindow } from 'electron';
import { onStatusChange } from '../lcu/state';
import { getIdentity, type Identity } from './identity';
import { Stopwatch } from './stopwatch';
import { readStore, updateStore, type PendingEvent } from './store';
import { enqueue } from './umami';
import { appMemoryMb } from './perfSampler';
import { isRecording } from '../highlight/highlight';

const SNAPSHOT_INTERVAL_MS = 60_000;

export type QuitSource = 'tray' | 'close-button' | 'system';

const startedAt = Date.now();
const homeVisible = new Stopwatch();
const clientConnected = new Stopwatch();
let lastIdentity: Identity | null = null;
let quitSource: QuitSource = 'system';
let timer: NodeJS.Timeout | null = null;
// 스냅샷 때마다 잰 앱 메모리 중 최대
let appMemMaxMb = 0;

function snapshot(quit: QuitSource | 'crash'): PendingEvent {
  lastIdentity = getIdentity() ?? lastIdentity;
  // 크래시면 마지막 스냅샷이 그대로 전송된다 → 죽기 직전(최대 1분 전) 녹화 중이었는지, 메모리가 얼마였는지 남는다.
  const appMemMb = appMemoryMb();
  appMemMaxMb = Math.max(appMemMaxMb, appMemMb);

  return {
    name: 'desktop-session-end',
    id: lastIdentity?.puuid,
    data: {
      riotId: lastIdentity?.riotId,
      totalSec: Math.round((Date.now() - startedAt) / 1000),
      homeVisibleSec: homeVisible.seconds(),
      clientConnectedSec: clientConnected.seconds(),
      quit,
      recording: isRecording(),
      appMemMb,
      appMemMaxMb,
    },
  };
}

// 지난 실행이 비정상 종료됐으면 마지막 스냅샷을 보낼 목록에 넣는다.
export function recoverLastSession() {
  const { openSession } = readStore();
  if (!openSession) return;

  enqueue(openSession);
  updateStore({ openSession: undefined });
}

export function startSessionTracking(home: BrowserWindow) {
  const syncHome = () => {
    if (home.isVisible() && !home.isMinimized()) homeVisible.start();
    else homeVisible.stop();
  };
  home.on('show', syncHome);
  home.on('hide', syncHome);
  home.on('minimize', syncHome);
  home.on('restore', syncHome);
  syncHome();

  onStatusChange((status) => {
    if (status === 'connected') clientConnected.start();
    else clientConnected.stop();
  });

  // 강제 종료에 대비해 1분마다 저장해 둔다.
  timer = setInterval(() => updateStore({ openSession: snapshot('crash') }), SNAPSHOT_INTERVAL_MS);
}

export function setQuitSource(source: QuitSource) {
  quitSource = source;
}

// 종료 직전엔 전송이 끊기므로 outbox에 넣고, 다음 실행 때 보낸다.
export function endSessionTracking() {
  if (timer !== null) clearInterval(timer);

  enqueue(snapshot(quitSource));
  updateStore({ openSession: undefined });
}
