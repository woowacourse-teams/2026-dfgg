import fs from 'node:fs';
import path from 'node:path';
import { planClips, videoOffset, type LiveEvent, type Stat } from './clipPlanner';
import { cutClip } from './cutter';
import { detectEncoder } from './encoder';
import type { HighlightProgress } from '../../shared/types';

export interface HighlightClip {
  file: string; // clips/ 안의 파일 이름
  start: number; // 원본 영상 기준 초
  end: number;
  events: number[]; // 포함된 EventID
}

// 한 줄에 JSON 하나인 파일 읽기 (앱이 갑자기 꺼지면 마지막 줄이 잘릴 수 있어서, 깨진 줄은 건너뜀)
function readJsonl<T>(file: string): T[] {
  if (!fs.existsSync(file)) return [];
  return fs
    .readFileSync(file, 'utf-8')
    .split('\n')
    .flatMap((line) => {
      if (!line.trim()) return [];
      try {
        return [JSON.parse(line) as T];
      } catch {
        return [];
      }
    });
}

function readJson<T>(file: string): T | null {
  try {
    // 메모장 등으로 저장하면 맨 앞에 BOM(\uFEFF)이 붙어 JSON.parse가 실패함 → 제거
    return JSON.parse(fs.readFileSync(file, 'utf-8').replace(/^\uFEFF/, '')) as T;
  } catch {
    return null;
  }
}

// 게임 후 호출: 세션 폴더의 데이터로 클립 계산 -> 잘라서 저장 -> clips.json 기록
// onProgress: 클립 하나 끝날 때마다 호출
export async function buildHighlight(
  dir: string,
  onProgress?: (p: HighlightProgress) => void,
): Promise<HighlightClip[]> {
  const t0 = Date.now(); // 전체 시작 시각
  const id = path.basename(dir); // 세션 폴더 이름 = 하이라이트 id

  const session = readJson<{ startedAt: number }>(path.join(dir, 'session.json'));
  const me = readJson<{ riotIdGameName: string }>(path.join(dir, 'me.json'));
  const encoder = await detectEncoder();
  if (!session || !me || !encoder) {
    // 필요한 정보가 없으면 만들 수 없음 (어떤 게 없는지 로그로 남김)
    console.warn('[highlight] 생성 불가', { session: !!session, me: !!me, encoder: !!encoder });
    return [];
  }

  const events = readJsonl<LiveEvent>(path.join(dir, 'events.jsonl'));
  const stats = readJsonl<Stat>(path.join(dir, 'stats.jsonl'));
  const offset = videoOffset(stats, session.startedAt);
  const clips = planClips(events, stats, me.riotIdGameName, offset);

  const outDir = path.join(dir, 'clips');
  fs.mkdirSync(outDir, { recursive: true });

  onProgress?.({ id, done: 0, total: clips.length, finished: false }); // 진행 상태 수정

  const results: HighlightClip[] = [];
  // 하나씩 순서대로 (동시에 돌리면 GPU를 나눠 써서 전체가 오히려 느려질 수 있음)
  for (const [i, clip] of clips.entries()) {
    const file = `clip-${String(i + 1).padStart(2, '0')}.mp4`; // clip-01.mp4
    const tClip = Date.now(); // 클립 하나 시작 시각
    try {
      await cutClip(path.join(dir, 'raw.mkv'), clip, path.join(outDir, file), encoder.name);
      const made = { file, ...clip };
      results.push(made); // 클립 파일과 클립 정보를 담은 results 배열에 저장

      onProgress?.({ id, done: results.length, total: clips.length, clip: made, finished: false }); // 진행 사항 업데이트

      const len = clip.end - clip.start;
      const took = (Date.now() - tClip) / 1000; // 영상 길이 / 걸린 시간 = 몇 배속으로 만들었는지
      console.log(
        `[highlight] ${file} ${len.toFixed(1)}초 영상 → ${took.toFixed(1)}초 (${(len / took).toFixed(1)}배속)`,
      );
    } catch (e) {
      // 이 클립만 건너뜀
      console.warn('[highlight] 클립 실패', file, (e as Error).message); // 이 클립만 건너뜀
    }
  }

  // 클립을 다 만들면 clips.json에 만든 클립 정보 results 배열 저장
  fs.writeFileSync(path.join(dir, 'clips.json'), JSON.stringify(results, null, 2));
  onProgress?.({ id, done: results.length, total: clips.length, finished: true }); // 진행 완료 상태 업데이트
  console.log(`[highlight] 전체 ${results.length}개 → ${((Date.now() - t0) / 1000).toFixed(1)}초`);
  return results;
}
