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
import { EXPANDED_WIDTH, EXPANDED_HEIGHT, COLLAPSED_HEIGHT, COLLAPSED_WIDTH } from '../constants';
import { setOverlayCollapsed } from '../analytics/gameTracker';
import { setQuitSource } from '../analytics/sessionTracker';
import { trackEvent, type EventData } from '../analytics/umami';
import type { FeedbackRating, EndedGame } from '../../shared/types';
import { submitFeedback } from '../api/feedback';

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
      : { width: EXPANDED_WIDTH, height: EXPANDED_HEIGHT };

    win.setResizable(true);
    win.setSize(width, height);
    win.setResizable(!collapsed);
    setOverlayCollapsed(collapsed);
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
