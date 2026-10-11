import fs from 'node:fs';
import path from 'node:path';
import { app } from 'electron';
import type { HighlightClip, HighlightGame } from '../../shared/types';

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

// 최근 판부터, 클립이 1개 이상 있는 판만
export function listHighlights(): HighlightGame[] {
  const root = highlightsRoot();
  if (!fs.existsSync(root)) return [];

  const games: HighlightGame[] = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const dir = path.join(root, entry.name);

    const clips = readJson<HighlightClip[]>(path.join(dir, 'clips.json'));
    if (!clips || clips.length === 0) continue; // 아직 만드는 중이거나 클립 없는 판은 제외

    const session = readJson<{ startedAt: number }>(path.join(dir, 'session.json')); // 정렬에 쓸 게임 시작 시각
    games.push({
      id: entry.name,
      startAt: session?.startedAt ?? fs.statSync(dir).mtimeMs, // session.json이 없으면 폴더 수정 시각
      clips,
    });
  }

  return games.sort((a, b) => b.startAt - a.startAt).slice(0, MAX_GAMES);
}
