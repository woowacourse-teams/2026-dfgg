import fs from 'node:fs';
import path from 'node:path';
import { app } from 'electron';
import { highlightsRoot } from './library';

const MAX_GAMES = 20; // 보관할 최대 판 수
const MIN_FREE_BYTES = 10 * 1024 ** 3; // 10GB 미만이면 녹화 안 함
const SESSION_NAME = /^\d{4}-\d{2}-\d{2}T\d{2}-\d{2}-\d{2}-\d{3}Z$/; // 우리가 만든 세션 폴더 이름 형식
const TEMP_FILES = ['raw.mkv', 'ffmpeg.log', 'events.jsonl', 'stats.jsonl', 'me.json']; // 클립 완성 후 필요 없는 파일

const keepFiles = () => !app.isPackaged; // dev에서는 원본·로그 유지 (튜닝, 디버깅용)

// 세션 폴더 목록. 이름이 날짜 형식인 폴더만 = 우리가 만든 폴더만 (다른 폴더는 절대 안 건드림)
function sessionDirs(): string[] {
  const root = highlightsRoot();
  if (!fs.existsSync(root)) return [];
  return fs
    .readdirSync(root, { withFileTypes: true })
    .filter((e) => e.isDirectory() && SESSION_NAME.test(e.name))
    .map((e) => path.join(root, e.name));
}

// 삭제 실패(재생 중이라 파일이 잠김 등)해도 앱은 계속 동작. 다음 정리 때 다시 시도됨
function remove(target: string) {
  try {
    fs.rmSync(target, { recursive: true, force: true, maxRetries: 3 });
  } catch (e) {
    console.warn('[highlight] 삭제 실패', target, (e as Error).message);
  }
}

// 게임 시작 시: 남은 용량 확인
export async function hasEnoughSpace(): Promise<boolean> {
  try {
    const s = await fs.promises.statfs(app.getPath('userData'));
    return s.bavail * s.bsize >= MIN_FREE_BYTES; // 쓸 수 있는 블록 수 × 블록 크기 = 남은 바이트
  } catch {
    return true;
  }
}

// 클립 완성 직후: 0클립이면 폴더 째, 아니면 원본 및 로그만 삭제
export function cleanupSession(dir: string, clipCount: number) {
  if (keepFiles()) return;
  if (clipCount === 0) return remove(dir);
  for (const f of TEMP_FILES) remove(path.join(dir, f));
}

// 클립 완성 직후: 완성된 판(clips.json 있음)이 20개를 넘으면 오래된 것부터 삭제
// 녹화 중인 판은 clips.json이 없어서 자동으로 제외됨
export function enforceLimit() {
  const done = sessionDirs().filter((d) => fs.existsSync(path.join(d, 'clips.json')));
  done.sort().reverse(); // 폴더 이릉미 ISO 시각이라 문자열 정렬 = 시간순 -> 뒤집으면 최신순
  for (const d of done.slice(MAX_GAMES)) remove(d);
}

// 앱 시작 시: 클립을 못 만든 판(clips.json 없음) 삭제. 녹화 시작 전에 호출해야 함
export function cleanupOrphans() {
  if (keepFiles()) return;
  for (const d of sessionDirs()) {
    if (!fs.existsSync(path.join(d, 'clips.json'))) remove(d);
  }
}
