import fs from 'node:fs';
import path from 'node:path';
import { app } from 'electron';
import type { HighlightClip, HighlightGame, HighlightSummary } from '../../shared/types';

const MAX_GAMES = 20; // 화면에 보여줄 최근 판수

// 하이라이트 최상위 폴더 (recorder가 세션 폴더를 만드는 곳과 같아야 함)
export function highlightsRoot() {
  return path.join(app.getPath('userData'), 'highlights');
}

function readJson<T>(file: string): T | null {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf-8').replace(/^\uFEFF/, '')) as T;
  } catch {
    return null;
  }
}

// 세션 폴더 하나 -> HighlightGame. 볼 수 있는 클립이 없으면 null
function readGame(dir: string, id: string): HighlightGame | null {
  const all = readJson<HighlightClip[]>(path.join(dir, 'clips.json'));
  const clips = (all ?? []).filter((c) => fs.existsSync(path.join(dir, 'clips', c.file)));
  const session = readJson<{ startedAt: number }>(path.join(dir, 'session.json')); // 정렬에 쓸 게임 시작 시각
  const match = readJson<{ gameId: number }>(path.join(dir, 'match.json'));

  return {
    id,
    gameId: match?.gameId ?? null,
    startAt: session?.startedAt ?? fs.statSync(dir).mtimeMs, // session.json이 없으면 폴더 수정 시각
    clips,
  };
}

// 볼 수 있는 모든 판, 최신수
function scanGames(): HighlightGame[] {
  const root = highlightsRoot();
  if (!fs.existsSync(root)) return [];

  const games: HighlightGame[] = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const dir = path.join(root, entry.name);
    const game = readGame(dir, entry.name);
    if (game) games.push(game);
  }

  return games.sort((a, b) => b.startAt - a.startAt);
}

// 최근 판부터, 클립이 1개 이상 있는 판만
export function listHighlights(): HighlightGame[] {
  return scanGames().slice(0, MAX_GAMES);
}

// 전적 목록용: 넘겨준 gameId 중 하이라이트가 있는 판만 { gameId: 요약 }
export function highlightSummary(gameIds: number[]): Record<number, HighlightSummary> {
  const wanted = new Set(gameIds);
  const result: Record<number, HighlightSummary> = {};

  for (const game of scanGames()) {
    // gameId가 없거나 전적 화면에 없는 판(21번째 이전 판 등)은 제외
    if (game.gameId === null || !wanted.has(game.gameId)) continue;
    if (result[game.gameId]) continue; // 같은 판이 폴더 2개면 최신 폴더만 쓴다.

    result[game.gameId] = {
      id: game.id, // 세션 폴더 이름. 재생할 때 주소를 만들 때 사용.
      count: game.clips.length, // 클립 수
      duration: game.clips.reduce((sum, c) => sum + (c.end - c.start), 0),
    };
  }
  return result;
}

// 재생용
export function getHighlight(gameId: number): HighlightGame | null {
  return scanGames().find((g) => g.gameId === gameId) ?? null;
}
