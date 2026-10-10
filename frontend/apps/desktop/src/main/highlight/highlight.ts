import { desktopCapturer } from 'electron';
import type { GameflowPhase } from '../../shared/types';
import { getLcuState, onPhaseChange } from '../lcu/state';
import { startRecording, stopRecording } from './recoder';
import { startEventCollector, stopEventCollector } from './eventCollector';
import { buildHighlight } from './pipeline';

let sessionDir: string | null = null; // 이번 판 세션 폴더 (녹화 시작 시 생성)

const GAME_WINDOW_TITLE = 'League of Legends (TM) Client'; // 롤 게임 창 제목
const WINDOW_POLL_MS = 2_000; // 게임 창 2초마다 확인

// 번호표: 정지할 때마다 1씩 올라감 => 기다리던 시작 작업이 자기 번호가 아니면 그만둠
let gameToken = 0;

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const isInGame = () => getLcuState().phase === 'InProgress';

// 롤 게임 창이 지금 떠 있는지 확인 (창 목록만 보고, 화면은 캡쳐 안 함)
async function hasGameWindow() {
  const sources = await desktopCapturer.getSources({
    types: ['window'],
    thumbnailSize: { width: 0, height: 0 },
  });
  return sources.some((s) => s.name.startsWith(GAME_WINDOW_TITLE));
}

// 게임 중일 때, 녹화를 시작함
async function start() {
  const token = ++gameToken; // 이번 녹화 작업 토큰 번호

  // 1. 게임 창이 생길 때까지 기다림
  while (!(await hasGameWindow())) {
    if (!isInGame() || token !== gameToken) return;
    await sleep(WINDOW_POLL_MS);
  }

  if (!isInGame() || token !== gameToken) return;

  // 2. 녹화 시작
  const session = await startRecording();

  // 3. startRecording을 기다리는 사이에 정지 요청이 왔으면 바로 정지
  if (token !== gameToken) {
    await stopRecording();
    return;
  }
  console.log('[highlight] 녹화 시작', session?.dir ?? '인코더 없음');
  if (session) {
    sessionDir = session.dir;
    startEventCollector(session.dir);
  }
}

// 게임 종료 시 녹화 종료
async function stop(build = true) {
  gameToken++; // 기다리던 start()가 있으면 취소됨
  const dir = sessionDir;
  sessionDir = null; // 진행 단계가 여러 번 바뀌어도 클립틍 한 번만 만들도록

  // 녹화 정지와 이벤트 저장 마무리를 동시에 기다림
  const [, session] = await Promise.all([stopEventCollector(), stopRecording()]);
  if (session) console.log('[highlight] 녹화 종료', session.videoPath);

  if (!dir || !build) return;
  const clips = await buildHighlight(dir);
  console.log(
    '[highlight] 클립 생성',
    clips.map((c) => `${c.file} (${(c.end - c.start).toFixed(1)}초)`),
  );
}

// 현재 phase에 따라서 실행할 함수를 정한다.
function handlePhase(phase: GameflowPhase | null) {
  // void를 붙여 start()를 await 하지 않고 다음 실행을 계속 이어 간다.
  if (phase === 'InProgress') void start();
  else void stop();
}

// main.ts의 whenReady에서 호출. 진행 단계 구독 시작
export function initHighlight() {
  onPhaseChange(handlePhase);
}

// main.ts의 will-quit에서 호출. 앱 종료 중에는 클립을 만들지 않음 (만드는 도중에 앱이 꺼짐)
export function endHighlight() {
  void stop(false);
}
