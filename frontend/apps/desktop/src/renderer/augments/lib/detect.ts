/**
 * 게임 화면에서 증강 선택 카드 세 장을 찾는다.
 *
 * 카드 크기·간격을 미리 정해 두지 않고 화면에서 직접 잰다. 해상도·화면 비율·HUD 크기에 따라 배치가 달라서다.
 * 카드 안쪽은 아주 어둡고 밝은 테두리로 둘러싸여 있다. 세로줄마다 밝기 중앙값을 보면 글자·아이콘에 흔들리지 않고
 * 어두운 안쪽 세 덩어리가 드러나고, 그 바깥에서 테두리가 끝나는 자리가 카드 경계다.
 */

/** RGBA 픽셀. canvas 의 ImageData 와 같은 모양 */
export type Frame = { width: number; height: number; data: Uint8ClampedArray };
export type Box = { x: number; y: number; width: number; height: number };

type Span = { start: number; end: number };

/** 안쪽은 이만큼 어둡고, 테두리는 안쪽보다 이만큼 밝아야 카드로 본다 (0~255). 등급마다 테두리 색이 달라 밝기 자체는 보지 않는다 */
const DARK_LUMA = 45;
const MIN_CONTRAST = 60;

/** 카드 안쪽을 찾을 때 훑는 화면 높이 구간. 카드가 작아져도 카드 안에 드는 곳이다 */
const SCAN_TOP = 0.3;
const SCAN_BOTTOM = 0.6;
const SCAN_ROWS = 33;
/** 제목 위쪽, 카드가 커지고 작아져도 안쪽에 드는 높이. 여기서 위로 올라가며 카드 윗변을 찾는다 */
const TOP_SCAN_START = 0.36;

/** 카드 안쪽 폭의 범위 (화면 높이 비율). HUD 크기 설정으로 커지고 작아지는 만큼을 담는다 */
const MIN_INSIDE_WIDTH = 0.15;
const MAX_INSIDE_WIDTH = 0.36;
/** 강조 빛이 번진 카드는 안쪽이 좁게 잡혀서 세 장의 폭이 이 배수까지 벌어질 수 있다 */
const MAX_WIDTH_SPREAD = 1.2;
/** 카드 간격의 범위와 양쪽 간격의 허용 차이 (안쪽 폭 비율) */
const MIN_PITCH = 1.1;
const MAX_PITCH = 1.7;
const MAX_PITCH_DIFF = 0.05;
/** 가운데 카드가 화면 가운데에서 어긋날 수 있는 만큼 (화면 높이 비율) */
const MAX_CENTER_SHIFT = 0.03;

/** 안쪽에서 테두리 끝을 찾는 거리 (안쪽 폭 비율). 테두리는 넘되 옆 카드에는 닿지 않는 만큼이다 */
const BORDER_REACH = 0.17;
/** 카드 높이 / 폭. 등급·해상도와 상관없이 같다 */
const CARD_ASPECT = 1.65;

function luma(frame: Frame, x: number, y: number) {
  const px = Math.min(frame.width - 1, Math.max(0, Math.round(x)));
  const py = Math.min(frame.height - 1, Math.max(0, Math.round(y)));
  const i = (py * frame.width + px) * 4;
  return 0.299 * frame.data[i] + 0.587 * frame.data[i + 1] + 0.114 * frame.data[i + 2];
}

const mean = (values: number[]) => values.reduce((sum, v) => sum + v, 0) / values.length;
const median = (values: number[]) =>
  [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)];
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
  // 강조 빛이 몇 군데 번져도 흔들리지 않게 중앙값을 쓴다
  return { border: median(border), inside: median(inside) };
}

function isCard(frame: Frame, box: Box) {
  const { border, inside } = measure(frame, box);
  return inside <= DARK_LUMA && border - inside >= MIN_CONTRAST;
}

/** 세로줄마다 밝기 중앙값 */
function columnBrightness(frame: Frame) {
  const rows = steps(SCAN_TOP * frame.height, SCAN_BOTTOM * frame.height, SCAN_ROWS);
  return Array.from({ length: frame.width }, (_, x) => median(rows.map((y) => luma(frame, x, y))));
}

/** 어두운 구간 중 minLength 이상인 것들 */
function darkSpans(profile: number[], minLength: number) {
  const spans: Span[] = [];
  let start: number | null = null;
  profile.forEach((value, index) => {
    if (value < DARK_LUMA) start ??= index;
    else {
      if (start !== null && index - start >= minLength) spans.push({ start, end: index });
      start = null;
    }
  });
  return spans;
}

const spanCenter = ({ start, end }: Span) => (start + end) / 2;
const spanWidth = ({ start, end }: Span) => end - start;

/** 폭이 비슷하고 간격이 고르며 화면 가운데에 놓인 이웃한 세 덩어리 */
function pickThree(spans: Span[], frame: Frame) {
  for (let i = 0; i + 3 <= spans.length; i++) {
    const three = spans.slice(i, i + 3);
    const widths = three.map(spanWidth);
    const width = Math.max(...widths);
    const [left, middle, right] = three.map(spanCenter);

    const similar = width <= Math.min(...widths) * MAX_WIDTH_SPREAD;
    const pitch = (right - left) / 2;
    const even = Math.abs(middle - left - (right - middle)) <= width * MAX_PITCH_DIFF;
    const spaced = pitch >= width * MIN_PITCH && pitch <= width * MAX_PITCH;
    const centered = Math.abs(middle - frame.width / 2) <= frame.height * MAX_CENTER_SHIFT;
    if (similar && even && spaced && centered) return three;
  }
  return null;
}

/**
 * 안쪽 끝(inner)에서 reach 만큼 바깥으로 나간 곳부터 다시 안쪽으로 오며 처음 밝아지는 자리.
 * 테두리 안에 어두운 틈이 있어 안쪽에서 나가면 틈에서 멈추므로 바깥에서 들어온다.
 */
function outerEdge(profile: number[], inner: number, direction: 1 | -1, reach: number) {
  const far = Math.min(profile.length - 1, Math.max(0, Math.round(inner + direction * reach)));
  const between = profile.slice(Math.min(inner, far), Math.max(inner, far) + 1);
  // 배경 밝기가 화면마다 달라서 테두리와 바깥의 가운데 밝기를 경계로 본다
  const threshold = (Math.max(...between) + profile[far]) / 2;

  for (let i = far; i !== inner; i -= direction) {
    if (profile[i] < threshold) continue;
    const outside = profile[i + direction] ?? profile[i];
    const past = outside < threshold ? (profile[i] - threshold) / (profile[i] - outside) : 0;
    return i + 0.5 + direction * past;
  }
  return inner + 0.5;
}

/** 카드 윗변. 아이콘을 피해 안쪽 양옆 줄의 가로줄별 중앙값을 본다 */
function topEdge(frame: Frame, span: Span) {
  const width = spanWidth(span);
  const columns = [
    ...steps(span.start + width * 0.15, span.start + width * 0.25, 6),
    ...steps(span.start + width * 0.75, span.start + width * 0.85, 6),
  ];
  const start = Math.round(TOP_SCAN_START * frame.height);
  const profile = Array.from({ length: start + 1 }, (_, y) =>
    median(columns.map((x) => luma(frame, x, y))),
  );

  let inner = start;
  while (inner > 0 && profile[inner - 1] < DARK_LUMA) inner--;
  return outerEdge(profile, inner, -1, width * BORDER_REACH);
}

/** 세 장이 모두 보이면 프레임 픽셀 좌표로 돌려준다. 하나라도 아니면 null */
export function detectCards(frame: Frame): Box[] | null {
  const profile = columnBrightness(frame);
  const minWidth = frame.height * MIN_INSIDE_WIDTH;
  const maxWidth = frame.height * MAX_INSIDE_WIDTH;
  const spans = darkSpans(profile, minWidth).filter((span) => spanWidth(span) <= maxWidth);
  const three = pickThree(spans, frame);
  if (!three) return null;

  const edges = three.map((span) => {
    const reach = spanWidth(span) * BORDER_REACH;
    return {
      left: outerEdge(profile, span.start, -1, reach),
      right: outerEdge(profile, span.end - 1, 1, reach),
    };
  });
  // 가운데 카드에 강조 빛이 번지면 그 카드만 부풀어 보여서 세 장의 중앙값을 쓴다
  const width = median(edges.map(({ left, right }) => right - left));
  const top = median(three.map((span) => topEdge(frame, span)));

  const boxes = edges.map(({ left, right }) => ({
    x: (left + right) / 2 - width / 2,
    y: top,
    width,
    height: width * CARD_ASPECT,
  }));
  return boxes.every((box) => isCard(frame, box)) ? boxes : null;
}

/**
 * 카드 안에서 제목 한 줄 (카드 크기 비율). 양옆 테두리와 아래 등급 태그는 OCR 을 흐려서 닿지 않게 두고,
 * 글자 윗부분이 잘리면 통째로 못 읽으므로 위쪽 여유를 넉넉히 둔다.
 */
export const TITLE_REGION = { x: 0.11, y: 0.417, width: 0.78, height: 0.086 };
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
