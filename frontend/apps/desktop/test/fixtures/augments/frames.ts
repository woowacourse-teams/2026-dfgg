/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { gunzipSync } from 'node:zlib';

import type { Box, Frame } from '../../../src/renderer/augments/lib/detect';

/**
 * 2880×1800 아수라장 녹화에서 뽑아 576×360 흑백으로 줄인 실제 화면.
 * 카드 찾기는 밝기만 쓰므로 흑백으로 저장해 크기를 줄였다. 오버레이가 뜨기 전 프레임만 골랐다.
 */
const WIDTH = 576;
const HEIGHT = 360;

export const NO_CARD_SCREEN = 'no-cards';

/** 카드가 떠 있는 화면. 어두운 맵과 밝은 맵, 강조 빛이 있는 가운데 카드를 모두 담았다 */
export const CARD_SCREENS = [
  'dark-map-tap-dancer',
  'dark-map-transcendence',
  'bright-map-transcendence',
  'bright-map-master-of-duality',
];

/** 원본 화면에서 잰 카드 바깥 테두리를 프레임 크기로 옮긴 값. 카드는 화면과 상관없이 이 자리에 있다 */
export const CARD_BOXES: Box[] = [
  { x: 111.5, y: 63.2, width: 107.6, height: 177.2 },
  { x: 234.1, y: 63.2, width: 107.6, height: 177.2 },
  { x: 357.2, y: 63.2, width: 107.6, height: 177.2 },
];

export function loadFrame(name: string): Frame {
  const gray = gunzipSync(readFileSync(join(__dirname, `${name}.gray.gz`)));
  const data = new Uint8ClampedArray(WIDTH * HEIGHT * 4);
  for (let i = 0; i < gray.length; i++) {
    data.set([gray[i], gray[i], gray[i], 255], i * 4);
  }
  return { width: WIDTH, height: HEIGHT, data };
}
