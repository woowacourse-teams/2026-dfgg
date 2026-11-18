import { execFile } from 'child_process';
import { promisify } from 'node:util';
import { getFfmpegPath } from './ffmpegPath';
import type { Clip } from './clipPlanner';
import { getWatermarkPath } from './ffmpegPath';

const execFileAsync = promisify(execFile);

// 왼쪽 위에 워터마크. 오른쪽 아래 미니맵, 오른쪽 위 점수판, 왼쪽 아래 채팅을 피한다.
const WATERMARK_FILTER =
  '[1:v]format=rgba,colorchannelmixer=aa=0.85[logo];' + // 로고 불투명도 85%
  '[0:v][logo]overlay=x=W*0.015:y=H*0.02:format=auto,' + // 화면 크기 비율로 위치 지정
  'format=nv12'; // 기존 -vf 와 같은 색 형식으로 맞춤

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
      clip.start.toFixed(2),
      '-t',
      duration.toFixed(2),
      '-i',
      input, // [0] 녹화 원본
      '-i',
      getWatermarkPath(), // [1] 워터마크 PNG
      '-filter_complex',
      WATERMARK_FILTER, // 기존 '-vf', 'format=nv12' 두 줄은 지우고 이걸로 바꿈
      '-c:v',
      encoder,
      '-b:v',
      '6M',
      '-an',
      '-movflags',
      '+faststart',
      output,
    ],
    { windowsHide: true, timeout: 120_000 },
  );
}
