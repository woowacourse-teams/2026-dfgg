/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { gunzipSync } from 'node:zlib';

import type { Box } from '../../../src/main/mayhem/cardDetect';
import type { Gray } from '../../../src/main/mayhem/titleMatch';

/** 2880×1800 아수라장 녹화에서 뽑아 576×360 흑백으로 줄인 실제 화면 */
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

/** 원본 화면에서 잰 카드 바깥 테두리를 프레임 크기로 옮긴 값 */
export const CARD_BOXES: Box[] = [
  { x: 111.5, y: 63.2, width: 107.6, height: 177.2 },
  { x: 234.1, y: 63.2, width: 107.6, height: 177.2 },
  { x: 357.2, y: 63.2, width: 107.6, height: 177.2 },
];

export function loadFrame(name: string): Gray {
  const data = new Uint8Array(gunzipSync(readFileSync(join(__dirname, `${name}.gray.gz`))));
  return { width: WIDTH, height: HEIGHT, data };
}
