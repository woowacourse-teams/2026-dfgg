/**
 * 게임 화면에서 증강 선택 카드 세 장을 찾는다.
 *
 * 롤 UI 는 화면 높이에 맞춰 커지고 가운데에 놓인다. 그래서 카드 크기·간격을 화면 높이 비율로 두고,
 * 그 근처 크기·위치를 조금씩 바꿔 가며 '밝은 테두리 + 어두운 안쪽'이 가장 뚜렷한 자리를 찾는다.
 * 해상도·화면 비율(16:9, 16:10, 21:9, 4:3)이 달라도, HUD 크기 설정으로 조금 커지거나 작아져도 찾는다.
 */

/** RGBA 픽셀. canvas 의 ImageData 와 같은 모양 */
export type Frame = { width: number; height: number; data: Uint8ClampedArray };
export type Box = { x: number; y: number; width: number; height: number };

/** 1280×719 한국어 화면에서 잰 카드. 모두 화면 높이에 대한 비율 */
const CARD = { width: 0.299, height: 0.494, top: 0.1615, pitch: 0.3338 };

/** 기준 크기에서 이만큼 커지고 작아진 경우까지 찾는다 (HUD·메뉴 크기 설정 대비) */
const SCALES = range(0.8, 1.2, 0.025);
/** 가운데·위치에서 이만큼 어긋난 경우까지 찾는다 (화면 높이 비율) */
const SHIFTS_Y = range(-0.04, 0.04, 0.0025);
const SHIFTS_X = range(-0.02, 0.02, 0.005);

/** 테두리는 이만큼 밝고, 안쪽은 이만큼 어두워야 카드로 본다 (0~255) */
const MIN_FRAME_LUMA = 95;
const MAX_INSIDE_LUMA = 45;
const MIN_CONTRAST = 60;

function range(from: number, to: number, step: number) {
  const count = Math.round((to - from) / step);
  return Array.from({ length: count + 1 }, (_, i) => from + i * step);
}

function luma(frame: Frame, x: number, y: number) {
  const px = Math.min(frame.width - 1, Math.max(0, Math.round(x)));
  const py = Math.min(frame.height - 1, Math.max(0, Math.round(y)));
  const i = (py * frame.width + px) * 4;
  return 0.299 * frame.data[i] + 0.587 * frame.data[i + 1] + 0.114 * frame.data[i + 2];
}

const mean = (values: number[]) => values.reduce((sum, v) => sum + v, 0) / values.length;
const steps = (from: number, to: number, count: number) =>
  Array.from({ length: count }, (_, i) => from + ((to - from) * i) / (count - 1));

/** 카드 한 장의 테두리 밝기와 안쪽 밝기 */
function measure(frame: Frame, box: Box) {
  const at = (rx: number, ry: number) =>
    luma(frame, box.x + box.width * rx, box.y + box.height * ry);

  // 바깥 테두리. 두께가 카드 폭의 4% 쯤이라 2% 안쪽을 짚는다
  const border = [
    ...steps(0.15, 0.85, 8).flatMap((ry) => [at(0.022, ry), at(0.978, ry)]),
    ...steps(0.2, 0.8, 6).map((rx) => at(rx, 0.014)),
  ];
  // 설명 글이 닿지 않는 안쪽 가장자리와 아랫부분
  const inside = [
    ...steps(0.25, 0.9, 6).flatMap((ry) => [at(0.13, ry), at(0.87, ry)]),
    ...steps(0.25, 0.75, 4).map((rx) => at(rx, 0.88)),
  ];
  // 테두리와 안쪽이 맞닿는 곳. 바로 바깥(밝음)과 바로 안(어두움)의 차이가 가장 클 때 자리가 딱 맞는다
  const edge =
    mean(steps(0.2, 0.8, 6).flatMap((ry) => [at(0.022, ry), at(0.978, ry)])) -
    mean(steps(0.2, 0.8, 6).flatMap((ry) => [at(0.075, ry), at(0.925, ry)])) +
    mean(steps(0.25, 0.75, 5).map((rx) => at(rx, 0.014))) -
    mean(steps(0.25, 0.75, 5).map((rx) => at(rx, 0.05)));
  return { border: mean(border), inside: mean(inside), edge };
}

function layout(frame: Frame, scale: number, dx: number, dy: number): Box[] {
  const h = frame.height;
  const width = CARD.width * h * scale;
  const height = CARD.height * h * scale;
  const pitch = CARD.pitch * h * scale;
  const centerX = frame.width / 2 + dx * h;
  // 카드는 화면 가운데보다 조금 위에 걸린다. 커지면 위아래로 함께 커지므로 가운데를 기준으로 키운다
  const centerY = (CARD.top + CARD.height / 2) * h + dy * h;
  return [-1, 0, 1].map((slot) => ({
    x: centerX + slot * pitch - width / 2,
    y: centerY - height / 2,
    width,
    height,
  }));
}

/** 세 장이 모두 보이면 프레임 픽셀 좌표로 돌려준다. 하나라도 아니면 null */
export function detectCards(frame: Frame): Box[] | null {
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

        // 가장 약한 카드로 겨룬다. 한 장만 또렷한 자리를 고르지 않게
        const score = Math.min(...cards.map(({ edge }) => edge));
        if (!best || score > best.score) best = { boxes, score };
      }
    }
  }
  return best?.boxes ?? null;
}

/** 카드 안에서 제목 한 줄 (카드 크기 비율) */
export const TITLE_REGION = { x: 0.06, y: 0.445, width: 0.88, height: 0.07 };
/** 카드 안에서 아이콘 */
export const ICON_REGION = { x: 0.22, y: 0.07, width: 0.56, height: 0.3 };

export function region(card: Box, part: Box): Box {
  return {
    x: card.x + card.width * part.x,
    y: card.y + card.height * part.y,
    width: card.width * part.width,
    height: card.height * part.height,
  };
}

/**
 * 제목·아이콘 부분을 작게 줄인 밝기 지문. 리롤이나 다음 선택으로 카드가 바뀌었는지 볼 때 쓴다.
 * OCR 은 비싸서 지문이 바뀐 카드만 다시 읽는다.
 */
export function fingerprint(frame: Frame, card: Box): number[] {
  const parts = [region(card, TITLE_REGION), region(card, ICON_REGION)];
  return parts.flatMap((part) =>
    steps(0.05, 0.95, 6).flatMap((ry) =>
      steps(0.05, 0.95, 12).map((rx) =>
        luma(frame, part.x + part.width * rx, part.y + part.height * ry),
      ),
    ),
  );
}

/** 지문 차이가 이보다 크면 다른 카드다 (픽셀당 평균 밝기 차) */
const CHANGED_DIFF = 14;

export function isSameCard(a: number[] | undefined, b: number[]): boolean {
  if (!a || a.length !== b.length) return false;
  return mean(a.map((v, i) => Math.abs(v - b[i]))) < CHANGED_DIFF;
}
