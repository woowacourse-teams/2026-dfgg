// 한 픽셀에 밝기 1바이트인 흑백 그림
export type Gray = { width: number; height: number; data: Uint8Array | Uint8ClampedArray };

// 글자 부분만 잘라 높이를 맞춘 그림. 값은 0(배경)~1(글자)
export type Mask = { width: number; height: number; data: Float32Array };

// 비교할 때 맞추는 글자 높이
export const MASK_HEIGHT = 32;
// 1등이 이 점수보 낮으면 어떤 후보와도 안 닮았다고 본다
export const MIN_TITLE_SCORE = 0.3;
// 1등이 2등보다 이만큼은 앞서야 고른다
export const MIN_TITLE_LEAD = 0.07;
// 폭이 다를수록 깎는 정도
const WIDTH_PENALTY = 2;
// 비교 전에 흐리는 정도. 폰트 차이를 덜 타게 한다
const BLUR_SIGMA = 1.2;

export type Crop = { left: number; right: number; bottom: number };
// 화면 제목 띠에서 버릴 부분: 양옆 카드 테두리, 아래 태그
export const SCREEN_CROP: Crop = { left: 0.04, right: 0.04, bottom: 0.28 };

// threshold 보다 밝은 픽셀(글자)만 남겨 글자 영역으로 자르고 높이를 MASK_HEIGHT로 맞춘다.
export function toMask(
  image: Gray,
  threshold: number,
  crop: Crop = { left: 0, right: 0, bottom: 0 },
): Mask | null {
  const { width, height, data } = image;

  // 1. 볼 범위: 양옆과 아래를 crop 비율만큼 뺀다
  const x0 = Math.floor(width * crop.left);
  const x1 = Math.ceil(width * (1 - crop.right));
  const y1 = Math.floor(height * (1 - crop.bottom));

  // 2. 글자 픽셀이 있는 가장 작은 사각형 찾기
  let left = x1;
  let right = -1;
  let top = y1;
  let bottom = -1;
  for (let y = 0; y < y1; y++) {
    for (let x = x0; x < x1; x++) {
      if (data[y * width + x] > threshold) {
        if (x < left) left = x;
        if (x > right) right = x;
        if (y < top) top = y;
        if (y > bottom) bottom = y;
      }
    }
  }
  if (right < 0) return null; // 글자 픽셀이 하나도 없다

  // 3. 그 사각형만 0/1로 옮기기
  const bw = right - left + 1;
  const bh = bottom - top + 1;
  const binary = new Float32Array(bw * bh);
  for (let y = 0; y < bh; y++) {
    for (let x = 0; x < bw; x++) {
      binary[y * bw + x] = data[(top + y) * width + left + x] > threshold ? 1 : 0;
    }
  }

  // 4. 비율을 유지한 채 높이를 MASK_HEIGHT로 맞추기
  const outWidth = Math.max(1, Math.round((bw * MASK_HEIGHT) / bh));
  return {
    width: outWidth,
    height: MASK_HEIGHT,
    data: resample(binary, bw, bh, outWidth, MASK_HEIGHT),
  };
}

// 넓이 평균으로 크기를 바꾼다. 줄일 때 픽셀을 건너뛰지 않아 글자가 깨지지 않는다
export function resample(
  src: Float32Array,
  sw: number,
  sh: number,
  dw: number,
  dh: number,
): Float32Array {
  const out = new Float32Array(dw * dh);
  const sx = sw / dw; // 새 픽셀 하나가 원본에서 가로로 덮는 길이
  const sy = sh / dh; // 세로로 덮는 길이

  for (let dy = 0; dy < dh; dy++) {
    const y0 = dy * sy;
    const y1 = y0 + sy;
    for (let dx = 0; dx < dw; dx++) {
      const x0 = dx * sx;
      const x1 = x0 + sx;

      // 새 픽셀이 덮는 원본 영역 [x0, x1) × [y0, y1) 의 평균
      let sum = 0;
      let area = 0;
      for (let y = Math.floor(y0); y < Math.ceil(y1); y++) {
        const wy = Math.min(y + 1, y1) - Math.max(y, y0); // 이 원본 줄이 겹치는 세로 길이
        for (let x = Math.floor(x0); x < Math.ceil(x1); x++) {
          const wx = Math.min(x + 1, x1) - Math.max(x, x0); // 겹치는 가로 길이
          sum += src[Math.min(y, sh - 1) * sw + Math.min(x, sw - 1)] * wx * wy;
          area += wx * wy;
        }
      }
      out[dy * dw + dx] = sum / area;
    }
  }
  return out;
}

// 가우시안 흐림 (가로 한 번, 세로 한 번)
function blur(src: Float32Array, w: number, h: number): Float32Array {
  // 흐림에 쓸 가중치 만들기: 가운데가 크고 양옆으로 갈수록 작다
  const radius = Math.ceil(BLUR_SIGMA * 3);
  const weights: number[] = [];
  for (let i = -radius; i <= radius; i++) {
    weights.push(Math.exp(-(i * i) / (2 * BLUR_SIGMA * BLUR_SIGMA)));
  }
  const total = weights.reduce((a, b) => a + b, 0);
  const kernel = weights.map((v) => v / total); // 합이 1이 되게

  // 한 방향으로 흐리기
  const pass = (input: Float32Array, horizontal: boolean) => {
    const out = new Float32Array(w * h);
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        let sum = 0;
        for (let i = -radius; i <= radius; i++) {
          const xx = horizontal ? Math.min(w - 1, Math.max(0, x + i)) : x;
          const yy = horizontal ? y : Math.min(h - 1, Math.max(0, y + i));
          sum += input[yy * w + xx] * kernel[i + radius];
        }
        out[y * w + x] = sum;
      }
    }
    return out;
  };

  return pass(pass(src, true), false); // 가로로 흐리고, 그 결과를 세로로 흐린다
}

/** 두 그림의 상관계수. 같은 모양이면 1, 관계없으면 0 근처 */
function correlation(a: Float32Array, b: Float32Array): number {
  // 평균
  let meanA = 0;
  let meanB = 0;
  for (let i = 0; i < a.length; i++) {
    meanA += a[i];
    meanB += b[i];
  }
  meanA /= a.length;
  meanB /= b.length;

  // 평균을 뺀 값끼리 곱해 더하고, 각자의 크기로 나눈다
  let ab = 0;
  let aa = 0;
  let bb = 0;
  for (let i = 0; i < a.length; i++) {
    const x = a[i] - meanA;
    const y = b[i] - meanB;
    ab += x * y;
    aa += x * x;
    bb += y * y;
  }
  return aa && bb ? ab / Math.sqrt(aa * bb) : 0;
}

/** 화면 제목과 후보 이름이 얼마나 닮았는지. 모양이 같으면 1 근처, 폭이 다를수록 깎인다 */
export function titleScore(screen: Mask, candidate: Mask): number {
  // 1. 후보를 화면과 같은 크기로 맞춘다 (높이는 둘 다 32, 폭만 맞춤)
  const stretched = resample(
    candidate.data,
    candidate.width,
    candidate.height,
    screen.width,
    screen.height,
  );

  // 2. 둘 다 흐린 뒤 모양 비교
  const corr = correlation(
    blur(screen.data, screen.width, screen.height),
    blur(stretched, screen.width, screen.height),
  );

  // 3. 원래 폭이 다른 만큼 깎는다
  const widthRatio = screen.width / candidate.width;
  return corr * Math.exp(-Math.abs(Math.log(widthRatio)) * WIDTH_PENALTY);
}

export type Scored<T> = { candidate: T; score: number };

export type TitlePick<T> =
  | { kind: 'picked'; picked: T; top: Scored<T>[] }
  | { kind: 'low' | 'close' | 'empty'; picked: null; top: Scored<T>[] };

/** 후보 중 화면 제목과 가장 닮은 하나를 고른다. 확실하지 않으면 picked 가 null */
export function pickByTitle<T>(
  screen: Mask,
  candidates: { candidate: T; mask: Mask }[],
): TitlePick<T> {
  const scored = candidates
    .map(({ candidate, mask }) => ({ candidate, score: titleScore(screen, mask) }))
    .sort((a, b) => b.score - a.score);
  const top = scored.slice(0, 3);
  const [first, second] = scored;

  if (!first) return { kind: 'empty', picked: null, top };
  if (first.score < MIN_TITLE_SCORE) return { kind: 'low', picked: null, top };
  if (second && first.score - second.score < MIN_TITLE_LEAD) {
    return { kind: 'close', picked: null, top };
  }
  return { kind: 'picked', picked: first.candidate, top };
}
