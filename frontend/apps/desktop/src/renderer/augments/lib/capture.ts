import type { Box, Frame } from './detect';

/** 카드 찾기는 이 높이로 줄인 화면에서 한다. 4K 에서도 같은 일만 한다 */
const DETECT_HEIGHT = 360;
/** OCR·아이콘 비교에 넘기는 그림 높이. 제목 글자가 tesseract 가 잘 읽는 크기가 된다 */
const TITLE_HEIGHT = 96;
const ICON_HEIGHT = 96;
/** 제목 둘레의 흰 여백. 글자가 그림 끝에 가까우면 tesseract 가 줄을 놓친다 */
const TITLE_MARGIN = 48;

/** main 이 넘겨준 게임 창만 받는다 (setDisplayMediaRequestHandler) */
export async function openGameStream() {
  const stream = await navigator.mediaDevices.getDisplayMedia({
    video: { frameRate: 5 },
    audio: false,
  });
  const video = document.createElement('video');
  video.muted = true;
  video.srcObject = stream;
  await video.play();
  return { video, stop: () => stream.getTracks().forEach((track) => track.stop()) };
}

const small = document.createElement('canvas');
const smallContext = small.getContext('2d', { willReadFrequently: true })!;

/** 줄인 화면 한 장 */
export function grabFrame(video: HTMLVideoElement): Frame | null {
  if (!video.videoWidth || !video.videoHeight) return null;
  small.height = DETECT_HEIGHT;
  small.width = Math.round((video.videoWidth * DETECT_HEIGHT) / video.videoHeight);
  smallContext.drawImage(video, 0, 0, small.width, small.height);
  return smallContext.getImageData(0, 0, small.width, small.height);
}

/**
 * 원본 해상도에서 한 부분을 잘라 PNG 로. box 는 화면 비율(0~1).
 * 제목은 흰 글자가 어두운 바탕에 있어 흑백을 뒤집어 검은 글자로 만든다. tesseract 가 더 잘 읽는다.
 */
async function crop(video: HTMLVideoElement, box: Box, height: number, filter: string, margin = 0) {
  const sx = box.x * video.videoWidth;
  const sy = box.y * video.videoHeight;
  const sw = box.width * video.videoWidth;
  const sh = box.height * video.videoHeight;
  const width = Math.max(1, Math.round((sw * height) / sh));
  const canvas = document.createElement('canvas');
  canvas.height = height + margin * 2;
  canvas.width = width + margin * 2;
  const context = canvas.getContext('2d')!;
  context.fillStyle = '#fff';
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.filter = filter;
  context.drawImage(video, sx, sy, sw, sh, margin, margin, width, height);
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
  if (!blob) throw new Error('카드 그림을 PNG 로 만들지 못함');
  return blob.arrayBuffer();
}

export const cropTitle = (video: HTMLVideoElement, box: Box) =>
  crop(video, box, TITLE_HEIGHT, 'grayscale(1) invert(1)', TITLE_MARGIN);

export const cropIcon = (video: HTMLVideoElement, box: Box) =>
  crop(video, box, ICON_HEIGHT, 'none');
