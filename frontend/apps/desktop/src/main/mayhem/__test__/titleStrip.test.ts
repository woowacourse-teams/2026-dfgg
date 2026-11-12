/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { gunzipSync } from 'node:zlib';
import { describe, expect, it } from 'vitest';
import { cutTitle, titleMask } from '../titleStrip';
import { pickByTitle, type Gray } from '../titleMatch';

const WIDTH = 100;
const HEIGHT = 60;
const CARD_HEIGHT = HEIGHT / 0.13; // 제목 영역이 카드 높이의 13%

/** bands 의 줄들을 x 20~79 에서 밝게 칠한 그림 */
function gray(bands: [number, number][]): Gray {
  const data = new Uint8Array(WIDTH * HEIGHT);
  for (const [top, bottom] of bands) {
    for (let y = top; y <= bottom; y++) {
      for (let x = 20; x < 80; x++) data[y * WIDTH + x] = 255;
    }
  }
  return { width: WIDTH, height: HEIGHT, data };
}

describe('cutTitle', () => {
  it('제목과 태그 중 위의 제목만 자른다', () => {
    const title = cutTitle(
      gray([
        [15, 30],
        [40, 50],
      ]),
      CARD_HEIGHT,
    );
    expect(title?.height).toBe(18); // 16줄 + 위아래 1줄씩
  });

  it('제목 위의 잡티는 무시한다', () => {
    expect(
      cutTitle(
        gray([
          [2, 2],
          [15, 30],
        ]),
        CARD_HEIGHT,
      )?.height,
    ).toBe(18);
  });

  it('제목이 위에서 잘렸으면 버린다', () => {
    expect(
      cutTitle(
        gray([
          [0, 12],
          [40, 50],
        ]),
        CARD_HEIGHT,
      ),
    ).toBeNull();
  });

  it('제목이 아래에서 잘렸으면 버린다', () => {
    expect(cutTitle(gray([[45, 59]]), CARD_HEIGHT)).toBeNull();
  });

  it('글자가 없으면 null', () => {
    expect(cutTitle(gray([]), CARD_HEIGHT)).toBeNull();
  });
});

describe('titleMask', () => {
  it('높이 32 마스크를 만든다', () => {
    expect(
      titleMask(
        gray([
          [15, 30],
          [40, 50],
        ]),
        CARD_HEIGHT,
      )?.height,
    ).toBe(32);
  });
});

describe('작은 화면의 실제 제목', () => {
  // 1280×800 창 모드에서 찍은 "위력 추구" 제목 띠와, 헷갈리던 두 견본
  const fixture = JSON.parse(
    gunzipSync(
      readFileSync(join(__dirname, '../../../../test/fixtures/augments/low-res-title.json.gz')),
    ).toString(),
  ) as {
    strip: { width: number; height: number; data: number[] };
    candidates: { name: string; width: number; height: number; data: number[] }[];
  };

  it('흐린 얇은 획도 살려 "가속 추구"와 가른다', () => {
    const strip = { ...fixture.strip, data: Uint8Array.from(fixture.strip.data) };
    const mask = titleMask(strip, strip.height / 0.13)!;
    const candidates = fixture.candidates.map(({ name, width, height, data }) => ({
      candidate: name,
      mask: { width, height, data: Float32Array.from(data) },
    }));
    const result = pickByTitle(mask, candidates);
    expect(result.kind).toBe('picked');
    expect(result.picked).toBe('위력 추구');
  });
});
