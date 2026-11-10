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

/**
 * 화면의 rect(물리 픽셀) 영역을 width×height 로 줄여서 찍는다. 실패하면 null.
 * 찍고 나면 Windows 자원(DC, 비트맵)을 바로 해제한다.
 */
export function captureRegion(rect: Rect, width: number, height: number): NativeImage | null {
  if (gdi === undefined) gdi = loadGdi();
  if (!gdi) return null;

  const screenDC = gdi.GetDC(null);
  if (!screenDC) return null;
  const memDC = gdi.CreateCompatibleDC(screenDC);
  const bitmap = gdi.CreateCompatibleBitmap(screenDC, width, height);
  const previous = gdi.SelectObject(memDC, bitmap);

  try {
    gdi.SetStretchBltMode(memDC, HALFTONE);
    gdi.SetBrushOrgEx(memDC, 0, 0, null);
    const copied = gdi.StretchBlt(
      memDC,
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

    const pixels = Buffer.alloc(width * height * 4);
    const info = {
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
    };
    const lines = gdi.GetDIBits(memDC, bitmap, 0, height, pixels, info, DIB_RGB_COLORS);
    if (lines !== height) return null;

    // 화면에서 읽은 픽셀은 알파가 0 이라 투명으로 보인다. 불투명으로 채운다
    for (let i = 3; i < pixels.length; i += 4) pixels[i] = 255;

    return nativeImage.createFromBitmap(pixels, { width, height });
  } finally {
    gdi.SelectObject(memDC, previous);
    gdi.DeleteObject(bitmap);
    gdi.DeleteDC(memDC);
    gdi.ReleaseDC(null, screenDC);
  }
}
