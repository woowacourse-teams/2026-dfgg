import type { Gray } from './titleMatch';

export type Box = { x: number; y: number; width: number; height: number };

/** 1280×719 한국어 화면에서 잰 카드. 모두 화면 높이에 대한 비율 */
const CARD = { width: 0.299, height: 0.494, top: 0.1615, pitch: 0.3338 };

/** 기준 크기에서 이만큼 커지고 작아진 경우까지 찾는다 (HUD 크기 설정 대비) */
const SCALES = range(0.8, 1.2, 0.025);
/** 기준 위치에서 이만큼 어긋난 경우까지 찾는다 (화면 높이 비율) */
const SHIFTS_Y = range(-0.04, 0.04, 0.0025);
const SHIFTS_X = range(-0.02, 0.02, 0.005);

/** 테두리는 이만큼 밝고, 안쪽은 이만큼 어두워야 카드로 본다 (0~255) */
const MIN_FRAME_LUMA = 95;
const MAX_INSIDE_LUMA = 45;
const MIN_CONTRAST = 60;

/** 카드 안의 제목 영역 (카드 크기 비율). 태그까지 넉넉히 잡고 titleStrip 에서 제목만 고른다 */
export const TITLE_REGION = { x: 0.06, y: 0.4, width: 0.88, height: 0.13 };

function range(from: number, to: number, step: number) {
  const count = Math.round((to - from) / step);
  return Array.from({ length: count + 1 }, (_, i) => from + i * step);
}

function luma(frame: Gray, x: number, y: number) {
  const px = Math.min(frame.width - 1, Math.max(0, Math.round(x)));
  const py = Math.min(frame.height - 1, Math.max(0, Math.round(y)));
  return frame.data[py * frame.width + px];
}

const mean = (values: number[]) => values.reduce((sum, v) => sum + v, 0) / values.length;
const steps = (from: number, to: number, count: number) =>
  Array.from({ length: count }, (_, i) => from + ((to - from) * i) / (count - 1));

/** 카드 한 장의 테두리 밝기, 안쪽 밝기, 경계의 뚜렷함 */
function measure(frame: Gray, box: Box) {
  const at = (rx: number, ry: number) =>
    luma(frame, box.x + box.width * rx, box.y + box.height * ry);

  const border = [
    ...steps(0.15, 0.85, 8).flatMap((ry) => [at(0.022, ry), at(0.978, ry)]),
    ...steps(0.2, 0.8, 6).map((rx) => at(rx, 0.014)),
  ];
  const inside = [
    ...steps(0.25, 0.9, 6).flatMap((ry) => [at(0.13, ry), at(0.87, ry)]),
    ...steps(0.25, 0.75, 4).map((rx) => at(rx, 0.88)),
  ];
  const edge =
    mean(steps(0.2, 0.8, 6).flatMap((ry) => [at(0.022, ry), at(0.978, ry)])) -
    mean(steps(0.2, 0.8, 6).flatMap((ry) => [at(0.075, ry), at(0.925, ry)])) +
    mean(steps(0.25, 0.75, 5).map((rx) => at(rx, 0.014))) -
    mean(steps(0.25, 0.75, 5).map((rx) => at(rx, 0.05)));
  return { border: mean(border), inside: mean(inside), edge };
}

function layout(frame: Gray, scale: number, dx: number, dy: number): Box[] {
  const h = frame.height;
  const width = CARD.width * h * scale;
  const height = CARD.height * h * scale;
  const pitch = CARD.pitch * h * scale;
  const centerX = frame.width / 2 + dx * h;
  const centerY = (CARD.top + CARD.height / 2) * h + dy * h;
  return [-1, 0, 1].map((slot) => ({
    x: centerX + slot * pitch - width / 2,
    y: centerY - height / 2,
    width,
    height,
  }));
}

/** 세 장이 모두 보이면 frame 픽셀 좌표로 돌려준다. 하나라도 아니면 null */
export function detectCards(frame: Gray): Box[] | null {
  let best: { boxes: Box[]; score: number } | null = null;

  for (const scale of SCALES) {
    for (const dy of SHIFTS_Y) {
      for (const dx of SHIFTS_X) {
        const boxes = layout(frame, scale, dx, dy);
        if (boxes[0].x < 0 || boxes[2].x + boxes[2].width > frame.width) continue;

        const cards = boxes.map((box) => measure(frame, box));
        const isCard = cards.every(
          ({ border, inside }) =>
            border >= MIN_FRAME_LUMA &&
            inside <= MAX_INSIDE_LUMA &&
            border - inside >= MIN_CONTRAST,
        );
        if (!isCard) continue;

        const score = Math.min(...cards.map(({ edge }) => edge));
        if (!best || score > best.score) best = { boxes, score };
      }
    }
  }
  return best?.boxes ?? null;
}

export function region(card: Box, part: Box): Box {
  return {
    x: card.x + card.width * part.x,
    y: card.y + card.height * part.y,
    width: card.width * part.width,
    height: card.height * part.height,
  };
}
