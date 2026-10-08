import { spawn, type ChildProcess } from 'node:child_process'; // 오래 도는 프로세스 실행
import fs from 'node:fs';
import path from 'node:path';
import { app } from 'electron';
import { getFfmpegPath } from './ffmpegPath';
import { detectEncoder } from './encoder';

// 캡처 설정: 노트북 테스트에서 확정한 값 그대로
const CAPTURE =
  'gfxcapture=window_exe=.*League of Legends.*' + // 롤 게임 창만
  ':width=1280:height=800:resize_mode=scale_aspect' + // 비율 유지하며 축소 (기본값 crop은 확대돼서 안 됨)
  ':max_framerate=30';

// 녹화 한 번에 대한 정보. 다음 단계(이벤트 기록, 자르기)에서 이 값을 씀
export interface RecordingSession {
  dir: string; // 세션 폴더: 영상, 로그, 나중에 events.jsonl도 여기 저장
  videoPath: string; // 원본 영상 경로 (raw.mkv)
  startedAt: number; // ffmpeg 실행 시각(ms). 나중에 "게임 시간 <-> 영상 시간" 계산에 사용
}

let proc: ChildProcess | null = null; // 실행 중인 ffmpeg 프로세스
let current: RecordingSession | null = null; // 진행 중인 세션 정보

// 녹화 시작, 사용처: 다음 단계에서 게임이 InProgress가 되면 호출
// 반환: 세션 정보
export async function startRecording(): Promise<RecordingSession | null> {
  if (proc) return current; // 이미 녹화 중이면 중복 실행 안 함 (재접속 등 두 번 불려도 안전)

  const encoder = await detectEncoder(); // 2단계 결과
  if (!encoder) return null;

  // 세션 폴더: %APPDATA%/<앱이름>/highlights/2026-10-08T07-50-00-000Z/
  const id = new Date().toISOString().replace(/[:.]/g, '-'); // 윈도우 파일명에 : 를 못 써서 치환
  const dir = path.join(app.getPath('userData'), 'highlights', id); // 비디오 저장 경로 설정
  fs.mkdirSync(dir, { recursive: true }); // 실제 폴더 생성. 중간 폴더 없으면 자동 생성
  const videoPath = path.join(dir, 'raw.mkv'); // ffmpeg를 해당 경로에 저장

  const args = [
    '-hide_banner', // 안내문 숨김
    '-y', // 같은 파일 있으면 덮어쓰기
    '-f', // 입력: gfxcapture
    'lavfi', // 입력: gfxcapture
    '-i', // 입력: gfxcapture
    CAPTURE, // 입력: gfxcapture
    '-vf', // GPU 프레임 → 인코더 (qsv면 hwmap)
    encoder.filter, // GPU 프레임 → 인코더 (qsv면 hwmap)
    '-c:v', // 인코더 (h264_qsv 등)
    encoder.name, // 인코더 (h264_qsv 등)
    '-b:v', // 비트레이트 6Mbps (분당 약 42MB)
    '6M', // 비트레이트 6Mbps (분당 약 42MB)
    '-r', // 출력 30fps 고정 (빈 프레임은 dup으로 채움)
    '30', // 출력 30fps 고정 (빈 프레임은 dup으로 채움)
    videoPath,
  ];

  // ffmpeg 실행
  const p = spawn(getFfmpegPath(), args, {
    stdio: ['pipe', 'ignore', 'pipe'], // [입력, 출력, 로그]
    windowsHide: true,
  });

  // 로그를 ffmpeg.log 파일로 저장. 녹화 이상하면 원인 분석 가능.
  p.stderr?.pipe(fs.createWriteStream(path.join(dir, 'ffmpeg.log')));

  // 끝났을 때 할 일 등록 예약
  p.once('exit', (code) => {
    console.log('[recorder] exit', code);
    proc = null;
    current = null;
  });

  // 현재 상태 저장하고 반환
  proc = p; // stopRecording()에서 정지할 때
  current = { dir, videoPath, startedAt: Date.now() }; // 정지 후 자르기 단계에 어느 폴더의 어느 영상인지 넘길 때
  return current; // 나중에 킬이 난 시각 -> 영상 몇 초 지점을 계산할 때 기준 시각
}

// 녹화 정시. 끝난 세션 정보 자르기 단게로 넘기기.
export function stopRecording(): Promise<RecordingSession | null> {
  const p = proc;
  const c = current;
  if (!p || !c) return Promise.resolve(null);

  return new Promise((resolve) => {
    // 종료를 누르고 10초 안에 안 끝나면 강제 종료
    const timer = setTimeout(() => p.kill(), 10_000);
    p.once('exit', () => {
      clearTimeout(timer);
      resolve(c); // 진짜 정지되었음을 호출한 쪽에 알려준다.
    });
    p.stdin?.write('q'); // 터미널에서 q 누르는 것과 같음 -> ffmpeg가 파일 마무리하고 종료
  });
}
