import type { EndedGame, FeedbackRating } from '../../shared/types';
import { apiPost } from './client';

export async function submitFeedback(rating: FeedbackRating, game: EndedGame) {
  const date = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
  const content = [
    `[desktop] ${rating}`,
    game.riotId,
    game.championId && `championId=${game.championId}`,
    game.queue,
    game.result,
    game.gameId && `gameId=${game.gameId}`,
  ]
    .filter(Boolean)
    .join('/');

  return apiPost<Record<string, string>>('/feedback', { date, content });
}
