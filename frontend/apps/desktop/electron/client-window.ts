import { type ChildProcess, spawn } from 'node:child_process';

/**
 * 롤 클라이언트 창의 화면상 위치를 감시한다.
 *
 * Electron 은 자기 창만 알 뿐 다른 프로세스의 창 좌표를 읽지 못한다. Win32 의
 * GetWindowRect 가 필요한데, 네이티브 모듈을 넣으면 electron-builder 재빌드와
 * MSIX 패키징이 복잡해진다. LCU 자격증명을 읽을 때와 같은 방식으로 PowerShell 에
 * 맡긴다.
 *
 * 다만 매번 프로세스를 띄우면 비용이 크므로, 한 번만 띄워놓고 그 안에서 루프를
 * 돌며 한 줄에 하나씩 좌표를 흘려보내게 한다.
 */
export interface ClientWindowRect {
  /** 물리 픽셀 기준. DIP 변환은 호출한 쪽에서 한다. */
  x: number;
  y: number;
  width: number;
  height: number;
  minimized: boolean;
}

const POLL_MS = 500;

const WATCH_SCRIPT = `
$ErrorActionPreference = 'SilentlyContinue'
Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class DfggWin {
  [StructLayout(LayoutKind.Sequential)]
  public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr hWnd);
  [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(IntPtr hWnd, int attr, out RECT value, int size);
}
"@
$RECT_SIZE = [System.Runtime.InteropServices.Marshal]::SizeOf([type]'DfggWin+RECT')
while ($true) {
  $line = 'null'
  $p = Get-Process -Name LeagueClientUx | Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
  if ($p) {
    $r = New-Object DfggWin+RECT
    # GetWindowRect 는 Windows 10 이후의 보이지 않는 리사이즈 테두리까지 포함해
    # 좌우 아래로 7~8px 씩 크게 나온다. 그대로 붙이면 틈이 뜨고 높이도 안 맞는다.
    # DWMWA_EXTENDED_FRAME_BOUNDS(9) 가 실제로 보이는 영역을 준다.
    $ok = [DfggWin]::DwmGetWindowAttribute($p.MainWindowHandle, 9, [ref]$r, $RECT_SIZE) -eq 0
    if (-not $ok) { $ok = [DfggWin]::GetWindowRect($p.MainWindowHandle, [ref]$r) }
    if ($ok) {
      $line = ConvertTo-Json -Compress @{
        x = $r.Left
        y = $r.Top
        width = $r.Right - $r.Left
        height = $r.Bottom - $r.Top
        minimized = [bool][DfggWin]::IsIconic($p.MainWindowHandle)
      }
    }
  }
  [Console]::Out.WriteLine($line)
  [Console]::Out.Flush()
  Start-Sleep -Milliseconds ${POLL_MS}
}
`;

function parseRect(line: string): ClientWindowRect | null {
  const trimmed = line.trim();
  if (!trimmed || trimmed === 'null') return null;
  try {
    const parsed = JSON.parse(trimmed) as Partial<ClientWindowRect>;
    // 창이 뜨는 중이면 0 이 잠깐 나온다. 그 값으로 붙이면 화면 구석으로 튄다.
    if (!parsed.width || !parsed.height) return null;
    return {
      x: parsed.x ?? 0,
      y: parsed.y ?? 0,
      width: parsed.width,
      height: parsed.height,
      minimized: parsed.minimized === true,
    };
  } catch {
    return null;
  }
}

/**
 * 감시를 시작한다. 좌표가 바뀔 때마다 콜백이 불린다. 클라이언트가 없으면 null.
 * 돌려주는 함수를 호출하면 감시를 멈춘다.
 */
export function watchClientWindow(onRect: (rect: ClientWindowRect | null) => void): () => void {
  if (process.platform !== 'win32') return () => {};

  // 따옴표 이스케이프를 신경 쓰지 않도록 스크립트를 통째로 인코딩해 넘긴다.
  const encoded = Buffer.from(WATCH_SCRIPT, 'utf16le').toString('base64');

  let child: ChildProcess;
  try {
    child = spawn('powershell', ['-NoProfile', '-NonInteractive', '-EncodedCommand', encoded], {
      windowsHide: true,
    });
  } catch (error) {
    console.error('[dock] 창 감시를 시작하지 못했습니다', error);
    return () => {};
  }

  let buffer = '';
  child.stdout?.on('data', (chunk: Buffer) => {
    buffer += chunk.toString();
    const lines = buffer.split('\n');
    // 마지막 조각은 아직 줄이 안 끝났을 수 있으니 남겨둔다.
    buffer = lines.pop() ?? '';
    for (const line of lines) onRect(parseRect(line));
  });

  child.on('error', (error) => console.error('[dock] 창 감시 오류', error));

  return () => {
    child.stdout?.removeAllListeners();
    child.kill();
  };
}
