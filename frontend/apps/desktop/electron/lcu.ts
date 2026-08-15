import { exec } from 'node:child_process';
import fs from 'node:fs';
import https from 'node:https';
import path from 'node:path';
import { promisify } from 'node:util';

import WebSocket from 'ws';

const execAsync = promisify(exec);

export interface LcuCredentials {
  port: number;
  password: string;
}

/** 롤 클라이언트가 안 떠 있으면 접속 정보를 못 찾는다. */
export class LcuNotRunningError extends Error {
  constructor() {
    super('롤 클라이언트를 찾지 못했습니다.');
    this.name = 'LcuNotRunningError';
  }
}

const LOCKFILE_CANDIDATES = [
  'C:\\Riot Games\\League of Legends\\lockfile',
  'C:\\Program Files\\Riot Games\\League of Legends\\lockfile',
  '/Applications/League of Legends.app/Contents/LoL/lockfile',
];

/** lockfile 형식: 이름:PID:포트:비밀번호:프로토콜 */
function parseLockfile(contents: string): LcuCredentials | null {
  const parts = contents.trim().split(':');
  if (parts.length < 5) return null;
  const port = Number(parts[2]);
  if (!Number.isFinite(port)) return null;
  return { port, password: parts[3] };
}

/**
 * 실행 중인 클라이언트 프로세스의 명령행에서 접속 정보를 읽는다.
 * 설치 경로를 몰라도 되고 lockfile 권한 문제도 피할 수 있어 이쪽을 먼저 시도한다.
 */
async function readFromProcess(): Promise<LcuCredentials | null> {
  if (process.platform !== 'win32') return null;

  try {
    const { stdout } = await execAsync(
      'powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \\"name = \'LeagueClientUx.exe\'\\" | Select-Object -ExpandProperty CommandLine"',
      { windowsHide: true },
    );

    const port = stdout.match(/--app-port=(\d+)/);
    const token = stdout.match(/--remoting-auth-token=([\w-]+)/);
    if (!port || !token) return null;

    return { port: Number(port[1]), password: token[1] };
  } catch {
    return null;
  }
}

function readFromLockfile(): LcuCredentials | null {
  for (const candidate of LOCKFILE_CANDIDATES) {
    try {
      return parseLockfile(fs.readFileSync(path.normalize(candidate), 'utf8'));
    } catch {
      // 다음 후보 경로를 시도한다.
    }
  }
  return null;
}

export async function findCredentials(): Promise<LcuCredentials> {
  const credentials = (await readFromProcess()) ?? readFromLockfile();
  if (!credentials) throw new LcuNotRunningError();
  return credentials;
}

function authHeader({ password }: LcuCredentials): string {
  return `Basic ${Buffer.from(`riot:${password}`).toString('base64')}`;
}

/**
 * LCU는 자체 서명 인증서를 쓴다. 검증을 끄되 이 요청에만 적용한다 —
 * NODE_TLS_REJECT_UNAUTHORIZED 같은 전역 스위치는 백엔드 통신까지 무방비로 만든다.
 * (fetch로는 요청 단위로 인증서 검증을 끌 수 없어 https 모듈을 직접 쓴다.)
 */
export function lcuRequest<T>(credentials: LcuCredentials, endpoint: string): Promise<T> {
  return new Promise((resolve, reject) => {
    const request = https.request(
      {
        host: '127.0.0.1',
        port: credentials.port,
        path: endpoint,
        method: 'GET',
        headers: { Authorization: authHeader(credentials) },
        rejectUnauthorized: false,
      },
      (response) => {
        let body = '';
        response.on('data', (chunk) => (body += chunk));
        response.on('end', () => {
          if (response.statusCode !== 200) {
            reject(new Error(`LCU ${endpoint} ${response.statusCode}`));
            return;
          }
          try {
            resolve(JSON.parse(body));
          } catch (error) {
            reject(error);
          }
        });
      },
    );

    request.on('error', reject);
    request.end();
  });
}

/**
 * 롤의 창 모드를 읽는다. 0=전체 화면, 1=테두리 없음, 2=창 모드.
 *
 * 전체 화면은 DirectX 독점 모드라 어떤 오버레이도 위에 올라가지 못한다.
 * 사용자에게 이유를 알려주려고 확인한다.
 */
export async function fetchWindowMode(credentials: LcuCredentials): Promise<number | null> {
  try {
    const settings = await lcuRequest<{ General?: { WindowMode?: number } }>(
      credentials,
      '/lol-game-settings/v1/game-settings',
    );
    const mode = settings.General?.WindowMode;
    return typeof mode === 'number' ? mode : null;
  } catch {
    return null;
  }
}

// 창 모드를 대신 바꿔주는 기능은 없다.
//
// PATCH /lol-game-settings/v1/game-settings 로 구현할 수 있지만, Riot 의 LCU 정책은
// 승인된 엔드포인트만 쓰도록 제한한다. 나머지 호출이 전부 읽기인데 여기만 쓰기라
// 심사에서 걸릴 소지가 크다. 창 모드는 사용자가 직접 바꾸도록 안내만 한다.

/**
 * 한 판이 진행 중이라고 볼 단계들.
 *
 * 밴픽이 끝나고 게임이 뜨기까지 로딩 화면이 30~60초 이어지는데, 그동안
 * 밴픽 API도 인게임 API도 응답하지 않는다. 그 공백을 "게임 종료"로 오해하면
 * 추천이 사라졌다가 다시 분석된다. 단계를 직접 물어보면 그 구간을 정확히 구분할 수 있다.
 */
const IN_GAME_PHASES = new Set(['ChampSelect', 'GameStart', 'InProgress', 'Reconnect']);

/**
 * 지금 한 판이 진행 중인지. 조회에 실패하면 null 을 돌려주고,
 * 호출한 쪽이 기존 방식(연속 실패 횟수)으로 판단하게 둔다.
 */
export async function fetchInGame(credentials: LcuCredentials): Promise<boolean | null> {
  try {
    const phase = await lcuRequest<string>(credentials, '/lol-gameflow/v1/gameflow-phase');
    return IN_GAME_PHASES.has(phase);
  } catch {
    return null;
  }
}

export interface LcuEventHandlers {
  onChampSelect: (session: unknown) => void;
  onChampSelectEnd: () => void;
  onDisconnect: () => void;
}

/**
 * 밴픽 세션 이벤트를 구독한다. 반환된 함수를 부르면 연결을 닫는다.
 * REST 폴링 대신 WebSocket을 쓰므로 픽이 바뀌는 즉시 알림이 온다.
 */
export function subscribeChampSelect(
  credentials: LcuCredentials,
  handlers: LcuEventHandlers,
): () => void {
  const socket = new WebSocket(`wss://127.0.0.1:${credentials.port}`, 'wamp', {
    headers: { Authorization: authHeader(credentials) },
    rejectUnauthorized: false,
  });

  socket.on('open', () => {
    // WAMP 구독: [5, 이벤트명]. OnJsonApiEvent 는 모든 API 변경을 흘려보낸다.
    socket.send(JSON.stringify([5, 'OnJsonApiEvent_lol-champ-select_v1_session']));
  });

  socket.on('message', (raw) => {
    let payload: unknown;
    try {
      payload = JSON.parse(raw.toString());
    } catch {
      return;
    }
    if (!Array.isArray(payload) || payload.length < 3) return;

    const event = payload[2] as { eventType?: string; uri?: string; data?: unknown };

    // 구독을 걸어도 다른 리소스 이벤트가 섞여 오는 경우가 있어 uri로 한 번 더 거른다.
    if (event.uri && event.uri !== '/lol-champ-select/v1/session') return;

    console.log('[lcu] event', event.eventType, event.uri ?? '(uri 없음)');

    if (event.eventType === 'Delete') {
      handlers.onChampSelectEnd();
      return;
    }
    if (event.data) handlers.onChampSelect(event.data);
  });

  socket.on('close', handlers.onDisconnect);
  socket.on('error', handlers.onDisconnect);

  return () => socket.close();
}
