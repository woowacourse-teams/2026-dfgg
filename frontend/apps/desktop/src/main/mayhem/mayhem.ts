import {
  BrowserWindow,
  desktopCapturer,
  type DesktopCapturerSource,
  ipcMain,
  screen,
  session,
  webContents,
} from 'electron';

import { decideAugment, rankAugments, type AugmentNames } from '../../shared/augmentMatch';
import type { GameflowPhase, MayhemAugment, MayhemSession } from '../../shared/types';
import { trackEvent } from '../analytics/umami';
import { fetchMayhemAugments } from '../api/augments';
import { findWindowRect } from '../docking/clientWindow';
import { type Rect } from '../docking/docking';
import { GAME_WINDOW_TITLE } from '../highlight/highlight';
import { getLcuState, onPhaseChange } from '../lcu/state';
import { getDDragonData } from '../live/ddragon';
import { getActivePlayer, getGameStats, getPlayerList } from '../live/endpoints';
import { toChampionAlias } from '../live/payload';
import { mainLocale } from '../locale';
import { isOverlayEnabled } from '../overlay/visibility';
import { reportError } from '../sentry';
import { pickByIcon } from './iconMatch';
import { readTitle, stopOcr, warmUpOcr } from './ocr';

/**
 * 아수라장(gameMode KIWI) 판에서 증강 선택 카드를 읽어 픽률을 얹는다.
 *
 * 증강 창은 게임 창 위에 겹친 투명한 창이다. 마우스는 그대로 게임으로 지나간다.
 * 창(렌더러)이 게임 화면을 받아 카드 세 장을 찾고, 바뀐 카드의 제목·아이콘을 잘라 보내면
 * 여기서 OCR 로 읽어 챔피언 증강 목록과 맞춘다.
 * 언제 선택 창이 뜨는지(레벨업·죽음·리롤·몰아서 고르기)는 따로 맞히지 않고 화면에 떠 있는지만 본다.
 */

const MAYHEM_MODE = 'KIWI';
const RETRY_MS = 3000;
/** 게임 창 위치를 따라가는 주기 */
const FOLLOW_MS = 1000;
/** 렌더러가 보내는 잘린 그림의 상한. 제목 한 줄과 아이콘이라 이보다 클 일이 없다 */
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;

let augmentWindow: BrowserWindow | null = null;
let gameToken = 0;
let followTimer: NodeJS.Timeout | null = null;
let gameSource: DesktopCapturerSource | null = null;
let current: MayhemSession | null = null;
/** 화면 언어의 증강. 카드에 그대로 보여 준다 */
let augments: MayhemAugment[] = [];
/** OCR 대조용. 게임 클라이언트 언어가 앱 언어와 달라도 맞도록 한·영 이름을 다 둔다 */
let augmentNames: AugmentNames[] = [];

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

async function retry<T>(alive: () => boolean, load: () => Promise<T | null>): Promise<T | null> {
  while (alive()) {
    const value = await load().catch(() => null);
    if (value) return value;
    await sleep(RETRY_MS);
  }
  return null;
}

/** 내 챔피언 숫자 id. Live Client 의 rawChampionName 을 ddragon 으로 바꾼다 */
async function findMyChampionId() {
  const [active, players, { championNames, championKeys }] = await Promise.all([
    getActivePlayer(),
    getPlayerList(),
    getDDragonData(),
  ]);
  const me = active?.riotId ?? active?.summonerName;
  const player = players?.find((p) => p.riotId === me || p.summonerName === me);
  if (!player) return null;
  return championKeys[toChampionAlias(player.rawChampionName, championNames)] ?? null;
}

async function loadAugments(championId: number) {
  const locale = mainLocale();
  const other = locale === 'ko' ? 'en' : 'ko';
  const [shown, alt] = await Promise.all([
    fetchMayhemAugments(championId, locale),
    fetchMayhemAugments(championId, other),
  ]);
  const altNames = new Map(alt.augments.map((augment) => [augment.augmentId, augment.name]));
  augments = shown.augments;
  augmentNames = shown.augments.map(({ augmentId, name }) => ({
    augmentId,
    names: [name, altNames.get(augmentId)].filter((n): n is string => Boolean(n)),
  }));
  return shown;
}

async function findGameSource() {
  const sources = await desktopCapturer.getSources({
    types: ['window'],
    thumbnailSize: { width: 0, height: 0 },
  });
  return sources.find((source) => source.name.startsWith(GAME_WINDOW_TITLE)) ?? null;
}

const sameRect = (a: Rect, b: Rect) =>
  a.x === b.x && a.y === b.y && a.width === b.width && a.height === b.height;

/** 증강 창을 게임 화면에 딱 겹친다. 게임 창이 안 보이면(최소화·알트탭) 숨긴다 */
function followGameWindow(win: BrowserWindow) {
  let last: Rect | null = null;
  const follow = () => {
    if (win.isDestroyed()) return;
    const rect = findWindowRect(GAME_WINDOW_TITLE);
    if (!rect) {
      last = null;
      if (win.isVisible()) win.hide();
      return;
    }
    // 윈도우 배율(125%·150%)이면 물리 픽셀과 창 좌표가 달라 바꿔 준다
    const bounds = screen.screenToDipRect(win, rect);
    if (!last || !sameRect(last, bounds)) win.setBounds(bounds);
    last = bounds;
    if (!win.isVisible()) win.showInactive();
  };
  follow();
  followTimer = setInterval(follow, FOLLOW_MS);
}

async function start() {
  const token = ++gameToken;
  const alive = () => token === gameToken && getLcuState().phase === 'InProgress';

  const stats = await retry(alive, getGameStats);
  if (!stats || stats.gameMode !== MAYHEM_MODE || !isOverlayEnabled() || !augmentWindow) return;

  const championId = await retry(alive, findMyChampionId);
  if (!championId) return;

  try {
    const loaded = await loadAugments(championId);
    if (loaded.augments.length === 0) return; // 이 챔피언 데이터가 아직 없다
  } catch {
    return; // apiGet 이 Sentry 로 보냈다. 이번 판은 증강 표시 없이 간다
  }
  warmUpOcr();

  gameSource = await retry(alive, findGameSource);
  if (!gameSource || !alive()) return;

  current = { championId };
  followGameWindow(augmentWindow);
  augmentWindow.webContents.send('mayhem:session', current);
  trackEvent('desktop-mayhem-start', { champion: championId, augments: augments.length });
}

function stop() {
  gameToken++;
  if (followTimer) clearInterval(followTimer);
  followTimer = null;
  gameSource = null;
  augments = [];
  augmentNames = [];
  if (!current) return;

  current = null;
  if (augmentWindow && !augmentWindow.isDestroyed()) {
    augmentWindow.webContents.send('mayhem:session', null);
    augmentWindow.hide();
  }
  void stopOcr();
}

function syncWithPhase(phase: GameflowPhase | null) {
  if (phase === 'InProgress') void start();
  else stop();
}

function toBuffer(value: unknown): Buffer | null {
  const bytes =
    value instanceof Uint8Array
      ? value
      : value instanceof ArrayBuffer
        ? new Uint8Array(value)
        : null;
  if (!bytes || bytes.byteLength === 0 || bytes.byteLength > MAX_IMAGE_BYTES) return null;
  return Buffer.from(bytes);
}

/** 카드 한 장의 제목·아이콘 그림으로 어떤 증강인지 찾는다. 확실하지 않으면 null */
async function readCard(titlePng: Buffer, iconPng: Buffer): Promise<MayhemAugment | null> {
  const text = await readTitle(titlePng);
  const decision = decideAugment(rankAugments(text, augmentNames));
  const byId = (id: number) => augments.find((augment) => augment.augmentId === id);

  if (decision.kind === 'match') return byId(decision.augmentId) ?? null;
  if (decision.kind === 'ambiguous') {
    const candidates = decision.augmentIds.map(byId).filter((a): a is MayhemAugment => !!a);
    return pickByIcon(iconPng, candidates);
  }
  return null;
}

function registerHandlers() {
  const fromAugmentWindow = (sender: Electron.WebContents) =>
    !!augmentWindow && !augmentWindow.isDestroyed() && sender === augmentWindow.webContents;

  ipcMain.handle('mayhem:get-session', (event) =>
    fromAugmentWindow(event.sender) ? current : null,
  );

  ipcMain.handle('mayhem:read-card', async (event, title: unknown, icon: unknown) => {
    const titlePng = toBuffer(title);
    const iconPng = toBuffer(icon);
    if (!fromAugmentWindow(event.sender) || !titlePng || !iconPng || !current) return null;
    try {
      return await readCard(titlePng, iconPng);
    } catch (error) {
      reportError('mayhem-read-card', error);
      return null;
    }
  });

  // 증강 창이 getDisplayMedia 를 부르면 게임 창만 넘긴다. 다른 창의 요청은 거절한다
  session.defaultSession.setDisplayMediaRequestHandler((request, callback) => {
    const requester = request.frame ? webContents.fromFrame(request.frame) : null;
    if (!gameSource || !requester || !fromAugmentWindow(requester)) return callback({});
    callback({ video: gameSource });
  });
}

export function initMayhem(win: BrowserWindow) {
  augmentWindow = win;
  registerHandlers();
  syncWithPhase(getLcuState().phase);
  onPhaseChange(syncWithPhase);
}
