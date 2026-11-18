import { type Rect } from './docking';
import { reportError } from '../sentry';

/** 롤 창을 직접 찾기 위한 user32 함수들. 실패하면 null 이고, 라이브러리 도킹만으로 동작한다. */
export function loadWin32() {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const koffi = require('koffi') as typeof import('koffi');
    const user32 = koffi.load('user32.dll');
    koffi.struct('RECT', { left: 'long', top: 'long', right: 'long', bottom: 'long' });
    koffi.struct('POINT', { x: 'long', y: 'long' });

    return {
      FindWindowW: user32.func(
        'void* __stdcall FindWindowW(const char16_t *cls, const char16_t *title)',
      ),
      GetClientRect: user32.func('bool __stdcall GetClientRect(void *hwnd, _Out_ RECT *rect)'),
      ClientToScreen: user32.func(
        'bool __stdcall ClientToScreen(void *hwnd, _Inout_ POINT *point)',
      ),
      IsWindowVisible: user32.func('bool __stdcall IsWindowVisible(void *hwnd)'),
      IsIconic: user32.func('bool __stdcall IsIconic(void *hwnd)'),
    };
  } catch (error) {
    console.warn('[창 찾기] koffi 로드 실패 — 롤 창을 직접 찾지 않습니다', error);
    reportError('docking-koffi', error);
    return null;
  }
}

// 처음 찾을 때 한 번만 불러온다. 실패해도 다시 시도하지 않는다.
let win32: ReturnType<typeof loadWin32> | undefined;

/** 롤 클라이언트 창의 안쪽 영역(물리 픽셀). 없거나 숨겨졌거나 최소화면 null. */
export function findWindowRect(title: string): Rect | null {
  if (win32 === undefined) win32 = loadWin32();
  if (!win32) return null;

  const hwnd = win32.FindWindowW(null, title);

  // 보이는 창일 때만 붙인다.
  if (!hwnd || !win32.IsWindowVisible(hwnd) || win32.IsIconic(hwnd)) return null;

  const size = { left: 0, top: 0, right: 0, bottom: 0 };
  const origin = { x: 0, y: 0 };

  if (!win32.GetClientRect(hwnd, size) || !win32.ClientToScreen(hwnd, origin)) return null;

  return { x: origin.x, y: origin.y, width: size.right, height: size.bottom };
}
