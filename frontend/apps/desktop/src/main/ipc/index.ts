import { app, BrowserWindow, ipcMain, shell } from 'electron';
import fs from 'node:fs';
import {
  fetchCurrentSummoner,
  fetchMatchHistory,
  fetchSummonerRankInfo,
  fetchMatchDetail,
  fetchEndedGame,
  fetchProfileBackground,
  fetchMatchDfScores,
} from '../lcu/service';
import { launchLeagueClient } from '../lcu/launch';
import { getLcuState } from '../lcu/state';
import {
  EXPANDED_WIDTH,
  EXPANDED_HEIGHT,
  COLLAPSED_HEIGHT,
  COLLAPSED_WIDTH,
  MIN_EXPANDED_HEIGHT,
  MAX_EXPANDED_HEIGHT,
} from '../constants';
import { recordOverlayClick, setOverlayCollapsed } from '../analytics/gameTracker';
import { setQuitSource } from '../analytics/sessionTracker';
import { trackEvent, type EventData } from '../analytics/umami';
import type { FeedbackRating, EndedGame, OverlayClickTarget } from '../../shared/types';
import { submitFeedback } from '../api/feedback';
import {
  getHighlight,
  highlightsRoot,
  highlightSummary,
  listHighlights,
} from '../highlight/library';
import { getLocale, updateSettings } from '../settings';
import { isAutoLaunchEnabled, setAutoLaunch } from '../autoLaunch/autoLaunch';
import { getDockingEnabled, setDockingEnabled } from '../docking/docking';
import { isOverlayEnabled, setOverlayEnabled } from '../overlay/visibility';
import { getHighlightEnabled, setHighlightEnabled } from '../highlight/highlight';
import { getMaxGames, setMaxGames } from '../highlight/storage';
import { reportError } from '../sentry';

const WEB_ORIGIN = 'https://dfgg.pro';

// 오버레이가 마지막으로 알려 준 펼친 높이. 접었다 펼 때 이 높이로 돌아간다.
let overlayHeight = EXPANDED_HEIGHT;

const CLICK_TARGETS = new Set(['item', 'collapse', 'expand', 'empty', 'other']);

// 창 크기를 바꾼다. 크기 조절을 막아 둔 창이라 잠깐 풀었다가 다시 잠근다.
function resizeWindow(win: BrowserWindow, width: number, height: number, resizable: boolean) {
  win.setResizable(true);
  win.setSize(width, height);
  win.setResizable(resizable);
}

function trackSettingChange(key: string, value: string | number | boolean) {
  trackEvent('desktop-setting-change', { key, value });
}

// ipc로 보내기
export function registerIpcHandlers() {
  ipcMain.handle('lcu:current-summoner', fetchCurrentSummoner);
  ipcMain.handle('lcu:get-state', getLcuState);
  ipcMain.handle('lcu:summoner-rank-info', fetchSummonerRankInfo);
  ipcMain.handle('lcu:profile-background', fetchProfileBackground);
  ipcMain.handle('lcu:launch-client', launchLeagueClient);
  ipcMain.handle('lcu:match-history', fetchMatchHistory);
  ipcMain.handle('lcu:match-detail', (_event, gameId: number) => fetchMatchDetail(gameId));
  // gameId 는 LCU 요청 주소에 그대로 들어가므로 정수인지 확인한다.
  ipcMain.handle('lcu:df-score', (_event, gameId: unknown) =>
    Number.isInteger(gameId) ? fetchMatchDfScores(gameId as number) : null,
  );

  ipcMain.on('window:set-collapsed', (event, collapsed: boolean) => {
    const win = BrowserWindow.fromWebContents(event.sender);
    if (!win) return;
    const { width, height } = collapsed
      ? { width: COLLAPSED_WIDTH, height: COLLAPSED_HEIGHT }
      : { width: EXPANDED_WIDTH, height: overlayHeight };

    resizeWindow(win, width, height, !collapsed);
    setOverlayCollapsed(collapsed);
  });
  // 오버레이가 내용 높이를 알려 주면 창 높이를 거기에 맞춘다. 추천 개수만큼 창이 늘고 준다.
  ipcMain.on('window:set-overlay-height', (event, height: unknown) => {
    const win = BrowserWindow.fromWebContents(event.sender);
    if (!win || typeof height !== 'number' || !Number.isFinite(height)) return;

    overlayHeight = Math.round(
      Math.min(MAX_EXPANDED_HEIGHT, Math.max(MIN_EXPANDED_HEIGHT, height)),
    );
    // 접혀 있는 동안에는 기억만 해 두고, 펼칠 때 쓴다.
    if (win.getSize()[0] !== EXPANDED_WIDTH) return;
    resizeWindow(win, EXPANDED_WIDTH, overlayHeight, true);
  });
  ipcMain.on('window:minimize', (event) => {
    BrowserWindow.fromWebContents(event.sender)?.minimize();
  });
  ipcMain.on('window:close', (event) => {
    setQuitSource('close-button');
    BrowserWindow.fromWebContents(event.sender)?.close();
  });
  ipcMain.on('analytics:track', (_event, name: string, data?: EventData) => {
    trackEvent(`desktop-ui-${name}`, data);
  });
  ipcMain.handle('lcu:ended-game', fetchEndedGame);
  ipcMain.handle('feedback:submit', (_event, rating: FeedbackRating, game: EndedGame) =>
    submitFeedback(rating, game),
  );
  // 하이라이트 모아보기용: 최근 20판 클립 목록 포함
  ipcMain.handle('highlight:list', () => listHighlights());
  // 전적 하이라이트용: 넘겨준 gameId 중 하이라이트 있는 판만
  ipcMain.handle('highlight:summary', (_event, gameIds) => {
    if (!Array.isArray(gameIds)) return {};
    return highlightSummary(gameIds.filter((id): id is number => Number.isInteger(id)));
  });
  // 하이라이트 하나 정보 가져오기
  ipcMain.handle('highlight:get', (_event, gameId) => {
    if (!Number.isInteger(gameId)) return null;
    return getHighlight(gameId as number);
  });
  ipcMain.on('overlay:click', (_event, target) => {
    if (typeof target === 'string' && CLICK_TARGETS.has(target)) {
      recordOverlayClick(target as OverlayClickTarget);
    }
  });
  ipcMain.on('locale:get', (event) => {
    event.returnValue = getLocale();
  });
  ipcMain.handle('locale:set', (_event, locale) => {
    if (locale !== 'ko' && locale !== 'en') return;
    updateSettings({ locale });
    trackSettingChange('locale', locale);
    for (const win of BrowserWindow.getAllWindows()) win.webContents.reload();
  });
  ipcMain.handle('autoLaunch:get', isAutoLaunchEnabled);
  ipcMain.handle('autoLaunch:set', (_event, enabled: boolean) => {
    if (typeof enabled !== 'boolean') return;
    setAutoLaunch(enabled);
    trackSettingChange('autoLaunch', enabled);
  });
  ipcMain.handle('docking:get', getDockingEnabled);
  ipcMain.handle('docking:set', (_event, enabled: boolean) => {
    if (typeof enabled !== 'boolean') return;
    setDockingEnabled(enabled);
    updateSettings({ docking: enabled });
    trackSettingChange('docking', enabled);
  });
  ipcMain.handle('overlay:get-enabled', isOverlayEnabled);
  ipcMain.handle('overlay:set-enabled', (_event, enabled: boolean) => {
    if (typeof enabled !== 'boolean') return;
    setOverlayEnabled(enabled);
    updateSettings({ showOverlay: enabled });
    trackSettingChange('showOverlay', enabled);
  });
  ipcMain.handle('highlight:get-enabled', getHighlightEnabled);
  ipcMain.handle('highlight:set-enabled', (_event, enabled: boolean) => {
    if (typeof enabled !== 'boolean') return;
    setHighlightEnabled(enabled);
    updateSettings({ highlight: enabled });
    trackSettingChange('highlight', enabled);
  });
  ipcMain.on('highlight:root', (event) => {
    event.returnValue = highlightsRoot();
  });
  ipcMain.handle('highlight:open-folder', async () => {
    const root = highlightsRoot();
    fs.mkdirSync(root, { recursive: true });
    const error = await shell.openPath(root);
    trackEvent('desktop-highlight-open-folder');
    if (error) reportError('highlight-open-folder', new Error(error));
  });
  ipcMain.handle('highlight:get-max-clip', getMaxGames);
  ipcMain.handle('highlight:set-max-clip', (_event, count: number) => {
    if (typeof count !== 'number') return;
    setMaxGames(count);
    updateSettings({ maxClipCount: count });
    trackSettingChange('maxGames', count);
  });
  ipcMain.on('app:version', (event) => {
    event.returnValue = app.getVersion();
  });
  ipcMain.handle('app:open-privacy', () => {
    shell.openExternal(`${WEB_ORIGIN}/${getLocale()}/privacy`);
    trackEvent('desktop-privacy-open');
  });
}
