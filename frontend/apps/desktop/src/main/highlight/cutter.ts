import { execFile } from 'child_process';
import { promisify } from 'node:util';
import { getFfmpegPath } from './ffmpegPath';
import type { Clip } from './clipPlanner';

const execFileAsync = promisify(execFile);

// raw.mkv에서 clip 구간만 잘라 mp4로 저장
export async function cutClip(input: string, clip: Clip, output: string, encoder: string) {
  const duration = clip.end - clip.start;

  await execFileAsync(
    getFfmpegPath(),
    [
      '-hide_banner',
      '-loglevel',
      'error',
      '-y',
      '-ss',
      clip.start.toFixed(2), // -i 앞에 두면: 가까운 키프레임으로 빠르게 이동 → 정확한 위치부터 디코딩
      '-t',
      duration.toFixed(2), // 이만큼만 읽음
      '-i',
      input,
      '-vf',
      'format=nv12', // 모든 하드웨어 인코더가 받는 색 형식 + Electron에서 재생 가능한 형식
      '-c:v',
      encoder, // 2단계에서 감지한 인코더 (h264_qsv 등)
      '-b:v',
      '6M',
      '-an', // 오디오 없음
      '-movflags',
      '+faststart', // mp4 목차를 파일 앞에 → 앱에서 바로 재생 시작
      output,
    ],
    { windowsHide: true, timeout: 120_000 }, // 클릭 하나에 2분 넘으면 포기
  );
}
