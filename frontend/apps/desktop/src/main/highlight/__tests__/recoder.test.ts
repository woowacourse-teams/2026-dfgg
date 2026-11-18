import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { readCaptureStats } from '../recoder';

vi.mock('electron', () => ({ app: { getPath: () => os.tmpdir() } }));
vi.mock('../../analytics/umami', () => ({ trackEvent: vi.fn() }));

describe('녹화 품질 지표 (readCaptureStats)', () => {
  let dir: string;

  beforeEach(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), 'recoder-'));
  });

  afterEach(() => {
    fs.rmSync(dir, { recursive: true, force: true });
  });

  const writeLog = (lines: string[]) =>
    fs.writeFileSync(path.join(dir, 'ffmpeg.log'), lines.join('\r'));

  it('마지막 진행 줄로 dup·drop 비율을 0~1 로 계산한다', () => {
    writeLog([
      'frame=  100 fps= 30 q=-1.0 size=1kB time=00:00:03.33 bitrate=1kbits/s dup=10 drop=0 speed=1.00x',
      // 저장 1000 = 실제 입력 900 + 복제 100, 입력 900 중 100 은 버렸으므로 들어온 프레임은 1000
      'frame= 1000 fps= 30 q=-1.0 size=9kB time=00:00:33.33 bitrate=1kbits/s dup=100 drop=100 speed=0.98x',
    ]);

    expect(readCaptureStats(dir)).toEqual({
      frames: 1000,
      dup: 100,
      drop: 100,
      dupRatio: 0.1,
      dropRatio: 0.1,
      speed: 0.98,
    });
  });

  it('입력이 30fps 보다 훨씬 많으면 drop 비율이 1 에 가깝다 (MF 인코더에서 보이던 수십만 drop)', () => {
    writeLog(['frame=60000 fps= 30 dup=0 drop=400000 speed=1.00x']);

    expect(readCaptureStats(dir).dropRatio).toBe(0.87);
  });

  it('로그가 없거나 진행 줄이 없으면 빈 값', () => {
    expect(readCaptureStats(dir)).toEqual({});

    writeLog(['Input #0, lavfi, from gfxcapture']);
    expect(readCaptureStats(dir)).toEqual({});
  });
});
