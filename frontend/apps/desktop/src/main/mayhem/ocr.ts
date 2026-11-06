import { app } from 'electron';
import path from 'node:path';
import type { Worker } from 'tesseract.js';
import { reportError } from '../sentry';

/** 한국어·영어 클라이언트 둘 다 읽는다. 처음 한 번 받아 사용자 폴더에 둔다 (각 2~4MB) */
const LANGUAGES = ['kor', 'eng'];

let worker: Promise<Worker> | null = null;

async function createOcrWorker() {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const { createWorker, PSM } = require('tesseract.js') as typeof import('tesseract.js');
  const created = await createWorker(LANGUAGES, 1, {
    cachePath: path.join(app.getPath('userData'), 'tesseract'),
  });
  // 제목은 한 줄짜리 글자다
  await created.setParameters({ tessedit_pageseg_mode: PSM.SINGLE_LINE });
  return created;
}

function getWorker() {
  if (!worker) {
    worker = createOcrWorker().catch((error) => {
      worker = null; // 언어 파일을 못 받았으면 다음 판에 다시 시도한다
      throw error;
    });
  }
  return worker;
}

/** 판이 시작될 때 미리 띄워 첫 카드를 기다리지 않게 한다 */
export function warmUpOcr() {
  getWorker().catch((error) => reportError('mayhem-ocr-init', error));
}

/** 제목 한 줄 그림(PNG)에서 글자를 읽는다 */
export async function readTitle(png: Buffer): Promise<string> {
  const { data } = await (await getWorker()).recognize(png);
  return data.text.trim();
}

export async function stopOcr() {
  const current = worker;
  worker = null;
  await current?.then((w) => w.terminate()).catch(() => undefined);
}
