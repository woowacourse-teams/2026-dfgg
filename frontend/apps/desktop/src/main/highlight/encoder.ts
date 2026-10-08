import { execFile } from 'node:child_process'; // 외부 exe를 실행하고 출력을 모아서 돌려준다.
import { promisify } from 'node:util'; // 콜백 방식 함수를 Promise로 바꾼다.
import { getFfmpegPath } from './ffmpegPath'; // ffmpeg 경로 준다.

// execFile의 Promise 버전이다. 4개의 테스트를 돌릴 때, spawnSync와 달리 main을 멈추지 않는다.
const execFileAsync = promisify(execFile);

// 후보 인코더 타입이다. ffmpeg '-c:v' 옵션에 그대로 들어가는 문자열이다.
export type EncoderName = 'h264_nvenc' | 'h264_amf' | 'h264_qsv' | 'h264_mf';

// detectEncoder()가 돌려주는 결과 모양이다. recorder.ts가 이 값을 받아 쓴다.
export interface EncoderProfile {
  name: EncoderName; // ffmpeg 인자 => '-c:v name'
  filter: string; // ffmpeg 인자 => '-vf filter'
}

const CANDIDATES: EncoderProfile[] = [
  // NVIDIA GPU 전용 인코딩 칩이에요. 게임 성능 영향이 가장 적어요.
  // hwdownload: GPU 프레임을 RAM으로 내려요 (안전한 기본값, 팀원 데스크톱 테스트 후 최적화 예정)
  { name: 'h264_nvenc', filter: 'hwdownload,format=bgra' },
  // AMD GPU 인코더예요.
  { name: 'h264_amf', filter: 'hwdownload,format=bgra' },
  // Intel 내장 그래픽(QSV)이에요. 노트북 테스트에서 검증한 방식 그대로예요.
  // hwmap: RAM을 거치지 않고 GPU 안에서 d3d11 → qsv로 바로 넘겨요
  { name: 'h264_qsv', filter: 'hwmap=derive_device=qsv,format=qsv' },
  // Windows Media Foundation이에요. 위 셋이 다 안 될 때 쓰는 최후의 수단이에요.
  { name: 'h264_mf', filter: 'hwdownload,format=bgra' },
];

// 인코더 하나가 이 PC에서 실제로 동작하는지 확인하기
async function canEncode(name: EncoderName) {
  try {
    // 터미널에서 아래 명령을 실해아는 것과 같다.
    // ffmpeg.exe -hide_banner -loglevel error -f lavfi -i color=black:s=256x256:d=0.2 -c:v <name> -f null -    await execFileAsync(
    await execFileAsync(
      getFfmpegPath(), // 실행할 프로그램
      [
        '-hide_banner', // 버전/빌드 안내문 숨김
        '-loglevel',
        'error', // 에러만 출력 (불필요한 로그 줄이기)
        '-f',
        'lavfi', // 입력이 파일이 아니라 FFmpeg 내부 생성기라는 뜻
        '-i',
        'color=black:s=256x256:d=0.2', // 256x256 검은 화면 0.2초 생성(테스트용)
        '-c:v',
        name, // 테스트할 인코더 (h264_nvenc)
        '-f',
        'null',
        '-', // 결과를 파일로 저장하지 않고 버림(디스크에 흔적이 안남음)
      ],
      {
        timeout: 5000, // 드라이버 문제로 멈춰도 5초 뒤에 강제 종료 -> reject
        windowsHide: true, // 설치된 앱에서 검은 콘솔 창이 깜빡이지 않게 함
      },
    );
    return true; // 여기까지 오면 ffmpeg가 종료 코드 0으로 끝난 것 -> 해당 인코더 사용 가능
  } catch {
    return false; // 종료 코드가 0이 아님 (인코더 없음) 또는 시간 초과 -> 사용 불가
  }
}

// 감지 결과를 저장해두기.
// null이면 아직 감지 안한 것. Promise는 감지 중이거나 감지 완료
let cached: Promise<EncoderProfile | null> | null = null;

// recorder.ts의 녹화 시작 함수에서 호출
// 반환값: 사용할 EncoderProfile 또는 null (이 PC에선 녹화 불가, 하이라이트 기능 끔)
export function detectEncoder() {
  // null일 때만 실행
  // 두 번째 호출부터는 이미 만든 Promise를 그대로 돌려줘서 앱 실행 중 딱 한번만 ffmpeg 테스트 한다.
  // 감지 도중에 또 호출돼도 같은 Promise를 기다리니 중복 테스트가 되지 않는다.
  cached ??= (async () => {
    // 하나씩 후보군 테스트한다. GPU 부담 막기 위해서
    for (const c of CANDIDATES) {
      if (await canEncode(c.name)) return c; // 처음 성공 인코더를 바로 결과로 돌린다.
    }
    return null; // 전부 실패 → 녹화 기능을 쓸 수 없는 PC
  })(); // 즉시 실행 async 함수: 만들자마자 실행되고, 그 Promise가 cached에 들어간다

  return cached;
}
