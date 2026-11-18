import { protocol } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import { Readable } from 'node:stream';
import { highlightsRoot } from './library';

export const MEDIA_SCHEME = 'dfgg-media';

// app ready 전에 호출해야 함. 이 스킴을 https처럼 취급해 달라고 등록
export function registerMediaScheme() {
  protocol.registerSchemesAsPrivileged([
    {
      scheme: MEDIA_SCHEME,
      privileges: {
        standard: true, // dfgg-media://host/path 형태의 일반 URL로 파싱
        secure: true, // https처럼 안전한 출처로 취급 (mixed content 차단 방지)
        stream: true, // <video></video>가 스트리밍으로 읽을 수 있게 (필수)
        supportFetchAPI: true, // fetch()로도 요청 가능 (테스트용)
        corsEnabled: true, // 다른 출처(localhost:3001)에서 요청 허용
      },
    },
  ]);
}

// app ready 후에 호출. 실제 요청을 처리
export function handleMediaProtocol() {
  protocol.handle(MEDIA_SCHEME, async (req) => {
    const url = new URL(req.url); // dfgg-media://clip/<id>/clips/clip-01.mp4
    if (url.hostname !== 'clip') return new Response('not found', { status: 404 });

    // 보안: highlights 폴더 밖이나 mp4가 아닌 파일은 거부
    const root = highlightsRoot();
    const file = path.resolve(root, decodeURIComponent(url.pathname.slice(1)));
    const rel = path.relative(root, file);
    if (rel.startsWith('..') || path.isAbsolute(rel) || path.extname(file) !== '.mp4') {
      return new Response('forbidden', { status: 403 });
    }

    try {
      return await serveFile(file, req.headers.get('range'));
    } catch {
      return new Response('not found', { status: 404 }); // 파일 없음
    }
  });
}

async function serveFile(file: string, range: string | null): Promise<Response> {
  const { size } = await fs.promises.stat(file);
  const base = {
    'Content-Type': 'video/mp4',
    'Accept-Ranges': 'bytes',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Expose-Headers': 'Content-Range',
  };

  const m = range ? /bytes=(\d*)-(\d*)/.exec(range) : null;
  if (!m) {
    // Range 없음 -> 파일 전체 (200)
    const body = Readable.toWeb(fs.createReadStream(file)) as ReadableStream;
    return new Response(body, {
      status: 200,
      headers: { ...base, 'Content-Length': String(size) },
    });
  }

  // "bytes=100-199", "bytes=100-", "bytes=-500"(끝에서 500바이트)
  const start = m[1] ? Number(m[1]) : Math.max(0, size - Number(m[2]));
  const end = m[1] && m[2] ? Math.min(Number(m[2]), size - 1) : size - 1;

  if (start >= size || start > end) {
    return new Response(null, { status: 416, headers: { 'Content-Range': `bytes */${size}` } });
  }

  const body = Readable.toWeb(fs.createReadStream(file, { start, end })) as ReadableStream;
  return new Response(body, {
    status: 206, // 부분 응답
    headers: {
      ...base,
      'Content-Length': String(end - start + 1),
      'Content-Range': `bytes ${start}-${end}/${size}`,
    },
  });
}
