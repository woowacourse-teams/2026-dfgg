import { nativeImage, type NativeImage } from 'electron';

import { type Rect } from '../docking/docking';
import { reportError } from '../sentry';

const SRCCOPY = 0x00cc0020; // 그대로 복사
const HALFTONE = 4; // 줄일 때 픽셀을 평균 내서 부드럽게
const DIB_RGB_COLORS = 0;
const BI_RGB = 0;

/** 화면을 찍는 데 쓰는 Windows 함수들. 실패하면 null */
function loadGdi() {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const koffi = require('koffi') as typeof import('koffi');
    const user32 = koffi.load('user32.dll');
    const gdi32 = koffi.load('gdi32.dll');

    koffi.struct('BITMAPINFOHEADER', {
      biSize: 'uint32',
      biWidth: 'int32',
      biHeight: 'int32',
      biPlanes: 'uint16',
      biBitCount: 'uint16',
      biCompression: 'uint32',
      biSizeImage: 'uint32',
      biXPelsPerMeter: 'int32',
      biYPelsPerMeter: 'int32',
      biClrUsed: 'uint32',
      biClrImportant: 'uint32',
    });

    return {
      GetDC: user32.func('void* __stdcall GetDC(void *hwnd)'),
      ReleaseDC: user32.func('int __stdcall ReleaseDC(void *hwnd, void *hdc)'),
      CreateCompatibleDC: gdi32.func('void* __stdcall CreateCompatibleDC(void *hdc)'),
      CreateCompatibleBitmap: gdi32.func(
        'void* __stdcall CreateCompatibleBitmap(void *hdc, int w, int h)',
      ),
      SelectObject: gdi32.func('void* __stdcall SelectObject(void *hdc, void *obj)'),
      DeleteObject: gdi32.func('bool __stdcall DeleteObject(void *obj)'),
      DeleteDC: gdi32.func('bool __stdcall DeleteDC(void *hdc)'),
      SetStretchBltMode: gdi32.func('int __stdcall SetStretchBltMode(void *hdc, int mode)'),
      SetBrushOrgEx: gdi32.func('bool __stdcall SetBrushOrgEx(void *hdc, int x, int y, void *old)'),
      StretchBlt: gdi32.func(
        'bool __stdcall StretchBlt(void *dst, int dx, int dy, int dw, int dh, void *src, int sx, int sy, int sw, int sh, uint32 rop)',
      ),
      GetDIBits: gdi32.func(
        'int __stdcall GetDIBits(void *hdc, void *bmp, uint32 start, uint32 lines, _Out_ uint8_t *bits, BITMAPINFOHEADER *info, uint32 usage)',
      ),
    };
  } catch (error) {
    console.warn('[화면 캡처] koffi 로드 실패', error);
    reportError('mayhem-capture-koffi', error);
    return null;
  }
}

// 처음 찍을 때 한 번만 불러온다
let gdi: ReturnType<typeof loadGdi> | undefined;
type Gdi = NonNullable<ReturnType<typeof loadGdi>>;

/**
 * 한 크기로 찍을 때 쓰는 Windows 자원과 픽셀 버퍼.
 * 0.5초마다 같은 크기로 찍으므로 매번 만들고 지우지 않고 다시 쓴다.
 */
type Surface = {
  memDC: unknown;
  bitmap: unknown;
  previous: unknown;
  pixels: Buffer;
  info: Record<string, number>;
};

// 크기별로 몇 개만 둔다. 판 화면 하나 + 제목 띠 몇 개면 충분하다
const MAX_SURFACES = 4;
const surfaces = new Map<string, Surface>();

function createSurface(api: Gdi, width: number, height: number): Surface | null {
  const screenDC = api.GetDC(null);
  if (!screenDC) return null;
  try {
    const memDC = api.CreateCompatibleDC(screenDC);
    const bitmap = api.CreateCompatibleBitmap(screenDC, width, height);
    const previous = api.SelectObject(memDC, bitmap);
    api.SetStretchBltMode(memDC, HALFTONE);
    api.SetBrushOrgEx(memDC, 0, 0, null);
    return {
      memDC,
      bitmap,
      previous,
      pixels: Buffer.alloc(width * height * 4),
      info: {
        biSize: 40,
        biWidth: width,
        biHeight: -height, // 음수 = 위에서 아래로 (그림 방향 그대로)
        biPlanes: 1,
        biBitCount: 32, // 픽셀당 4바이트 (B, G, R, 빈칸)
        biCompression: BI_RGB,
        biSizeImage: 0,
        biXPelsPerMeter: 0,
        biYPelsPerMeter: 0,
        biClrUsed: 0,
        biClrImportant: 0,
      },
    };
  } finally {
    api.ReleaseDC(null, screenDC);
  }
}

function destroySurface(api: Gdi, surface: Surface) {
  api.SelectObject(surface.memDC, surface.previous);
  api.DeleteObject(surface.bitmap);
  api.DeleteDC(surface.memDC);
}

function surfaceFor(api: Gdi, width: number, height: number): Surface | null {
  const key = `${width}x${height}`;
  const found = surfaces.get(key);
  if (found) {
    // 최근에 쓴 것을 맨 뒤로 보낸다
    surfaces.delete(key);
    surfaces.set(key, found);
    return found;
  }
  const created = createSurface(api, width, height);
  if (!created) return null;
  surfaces.set(key, created);
  // 너무 많으면 가장 오래 안 쓴 것부터 지운다
  while (surfaces.size > MAX_SURFACES) {
    const [oldKey, old] = surfaces.entries().next().value!;
    destroySurface(api, old);
    surfaces.delete(oldKey);
  }
  return created;
}

/** 판이 끝나면 모아 둔 Windows 자원을 돌려준다 */
export function releaseCaptureSurfaces() {
  if (!gdi) return;
  for (const surface of surfaces.values()) destroySurface(gdi, surface);
  surfaces.clear();
}

/**
 * 화면의 rect(물리 픽셀) 영역을 width×height 로 줄여서 찍고, 픽셀(B, G, R, 빈칸)을 돌려준다. 실패하면 null.
 * 돌려준 버퍼는 같은 크기로 다음에 찍을 때 다시 쓰이므로, 바로 쓰고 붙잡아 두지 않는다.
 */
export function captureRegionPixels(rect: Rect, width: number, height: number): Buffer | null {
  if (gdi === undefined) gdi = loadGdi();
  if (!gdi) return null;

  const surface = surfaceFor(gdi, width, height);
  if (!surface) return null;

  const screenDC = gdi.GetDC(null);
  if (!screenDC) return null;
  try {
    const copied = gdi.StretchBlt(
      surface.memDC,
      0,
      0,
      width,
      height,
      screenDC,
      rect.x,
      rect.y,
      rect.width,
      rect.height,
      SRCCOPY,
    );
    if (!copied) return null;

    const lines = gdi.GetDIBits(
      surface.memDC,
      surface.bitmap,
      0,
      height,
      surface.pixels,
      surface.info,
      DIB_RGB_COLORS,
    );
    return lines === height ? surface.pixels : null;
  } finally {
    gdi.ReleaseDC(null, screenDC);
  }
}

/** 픽셀을 PNG 로 저장할 수 있는 그림으로 만든다. 디버그 저장 때만 쓴다 */
export function pixelsToImage(pixels: Buffer, width: number, height: number): NativeImage {
  const copy = Buffer.from(pixels);
  // 화면에서 읽은 픽셀은 알파가 0 이라 투명으로 보인다. 불투명으로 채운다
  for (let i = 3; i < copy.length; i += 4) copy[i] = 255;
  return nativeImage.createFromBitmap(copy, { width, height });
}

/** 화면의 rect 영역을 찍어 그림으로 돌려준다. 실패하면 null */
export function captureRegion(rect: Rect, width: number, height: number): NativeImage | null {
  const pixels = captureRegionPixels(rect, width, height);
  return pixels && pixelsToImage(pixels, width, height);
}
