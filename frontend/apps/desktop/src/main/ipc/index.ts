import { BrowserWindow, ipcMain } from 'electron';
import {
  fetchCurrentSummoner,
  fetchMatchHistory,
  fetchSummonerRankInfo,
  fetchMatchDetail,
  fetchEndedGame,
  fetchProfileBackground,
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
import { setOverlayCollapsed } from '../analytics/gameTracker';
import { setQuitSource } from '../analytics/sessionTracker';
import { trackEvent, type EventData } from '../analytics/umami';
import type { FeedbackRating, EndedGame } from '../../shared/types';
import { submitFeedback } from '../api/feedback';

// 오버레이가 마지막으로 알려 준 펼친 높이. 접었다 펼 때 이 높이로 돌아간다.
let overlayHeight = EXPANDED_HEIGHT;

// 창 크기를 바꾼다. 크기 조절을 막아 둔 창이라 잠깐 풀었다가 다시 잠근다.
function resizeWindow(win: BrowserWindow, width: number, height: number, resizable: boolean) {
  win.setResizable(true);
  win.setSize(width, height);
  win.setResizable(resizable);
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
}
