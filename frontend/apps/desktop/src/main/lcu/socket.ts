import WebSocket from 'ws';
import type { LcuEvent, Lockfile } from '../../shared/types';
import { RIOT_ROOT_CERT } from './riotCert';

const SUBSCRIPTIONS = ['OnJsonApiEvent_lol-gameflow_v1_gameflow-phase'];
const HANDSHAKE_TIMEOUT_MS = 5000;

// lcu socket 연결 함수
export function connectLcuSocket(lockfile: Lockfile, onEvent: (payload: LcuEvent) => void) {
  const { port, password } = lockfile;
  const auth = Buffer.from(`riot:${password}`).toString('base64');

  const ws = new WebSocket(`wss://127.0.0.1:${port}`, {
    headers: { Authorization: `Basic ${auth}` },
    ca: RIOT_ROOT_CERT,
    handshakeTimeout: HANDSHAKE_TIMEOUT_MS,
  });

  ws.on('open', () => {
    for (const name of SUBSCRIPTIONS) ws.send(JSON.stringify([5, name]));
  });

  ws.on('message', (raw) => {
    const text = raw.toString();
    if (!text) return;
    try {
      const [opcode, , payload] = JSON.parse(text);

      if (opcode === 8) onEvent(payload);
    } catch {
      console.error('LCU 소켓 메시지 파싱 실패', text.slice(0, 200));
    }
  });

  ws.on('error', (error: unknown) => console.error('LCU 소켓 에러', error));

  return ws;
}
