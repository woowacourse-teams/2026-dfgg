/**
 * 개발 서버에서만 실리는 가짜 preload. 브라우저로 /augments/index.html 을 바로 열어 본다.
 *
 *   /augments/index.html                    가짜 증강 선택 화면을 그려 흘려 보낸다
 *   /augments/index.html?bg=/shot.png       같은 출처의 게임 스크린샷을 흘려 보낸다 (실제 화면으로 카드 찾기 확인)
 *
 * 화면 캡처 대신 canvas 를 영상으로 흘려 보내므로 카드 찾기·OCR 요청·꾸밈까지 실제와 같은 길로 지나간다.
 */
import type { MayhemAugment } from '../../shared/types';

const SAMPLES: MayhemAugment[] = [
  { augmentId: 1, name: '벼락', rarity: 'gold', rank: 3, pickRate: 9.4, iconUrl: '' },
  { augmentId: 2, name: '포로 발사기', rarity: 'gold', rank: 1, pickRate: 18.2, iconUrl: '' },
  { augmentId: 3, name: '흡혈병', rarity: 'gold', rank: null, pickRate: null, iconUrl: '' },
];

/** 실제 화면과 같은 비율로 카드 세 장을 그린다 (detect.test.ts 의 화면과 같은 모양) */
function drawFakeScreen(canvas: HTMLCanvasElement) {
  const { width, height } = canvas;
  const context = canvas.getContext('2d')!;
  context.fillStyle = '#1d2a33';
  context.fillRect(0, 0, width, height);
  const cardWidth = 0.299 * height;
  const cardHeight = 0.494 * height;
  const top = 0.1615 * height;
  for (const slot of [-1, 0, 1]) {
    const x = width / 2 + slot * 0.3338 * height - cardWidth / 2;
    context.fillStyle = '#dcbe96';
    context.fillRect(x, top, cardWidth, cardHeight);
    context.fillStyle = '#050f11';
    context.fillRect(
      x + cardWidth * 0.045,
      top + cardHeight * 0.03,
      cardWidth * 0.91,
      cardHeight * 0.94,
    );
  }
}

async function makeStream() {
  const canvas = document.createElement('canvas');
  const bg = new URLSearchParams(location.search).get('bg');
  if (bg) {
    const image = new Image();
    image.src = bg;
    await image.decode();
    canvas.width = image.naturalWidth;
    canvas.height = image.naturalHeight;
    canvas.getContext('2d')!.drawImage(image, 0, 0);
    // 스크린샷을 창 배경에도 깔아 꾸밈이 카드 위에 제대로 얹히는지 눈으로 본다
    document.body.style.background = `url(${bg}) center / 100% 100% no-repeat`;
  } else {
    canvas.width = 1920;
    canvas.height = 1080;
    drawFakeScreen(canvas);
    document.body.style.background = `url(${canvas.toDataURL()}) center / 100% 100% no-repeat`;
  }
  return canvas.captureStream(5);
}

if (!window.mayhem) {
  let reads = 0;
  navigator.mediaDevices.getDisplayMedia = makeStream;
  window.mayhem = {
    getSession: async () => ({ championId: 22 }),
    onSessionChange: () => () => undefined,
    readCard: async () => SAMPLES[reads++ % SAMPLES.length],
  };
  window.analytics = { track: () => undefined, overlayClick: () => undefined };
}
