import fs from 'node:fs';
import path from 'node:path';
import { app } from 'electron';
import { FALLBACK_DIRS, readInstallDirFromMetadata } from '../lcu/credentials';
import { getLcuState } from '../lcu/state';
import type { GameflowPhase } from '../../shared/types';

const IN_GAME: (GameflowPhase | null)[] = [
  'GameStart',
  'InProgress',
  'WaitingForStats',
  'PreEndOfGame',
];

const BORDERLESS = 2; // 테두리 없음 모드
const WINDOWED = 1; // 창모드

// 게임 설정 파일 찾기
function findGameConfigPath() {
  const candidates = [readInstallDirFromMetadata(), ...FALLBACK_DIRS]
    .filter((dir): dir is string => dir !== null)
    .map((dir) => path.join(dir, 'Config', 'game.cfg'));
  return candidates.find((p) => fs.existsSync(p)) ?? null;
}

// [General] 섹션 본문의 [시작, 끝) 인덱스
function findGeneralSection(text: string) {
  const header = text.match(/^\[General\][ \t]*\r?\n/m);
  if (header?.index === undefined) return;

  const start = header.index + header[0].length;
  const next = text.slice(start).search(/^\[/m);

  return { start, end: next === -1 ? text.length : start + next };
}

// 현재 게임 화면 모드 읽어서 반환하기
function readWindowMode(text: string) {
  const section = findGeneralSection(text);
  if (!section) return null;

  const match = text.slice(section.start, section.end).match(/^WindowMode=(\d+)/m);
  return match ? Number(match[1]) : null;
}

// 화면 모드를 2로 수정하기
function withWindowMode(text: string, mode: number) {
  const section = findGeneralSection(text);
  if (!section) return text;

  const body = text.slice(section.start, section.end);
  const updated = body.replace(/^(WindowMode=)\d+/m, `$1${mode}`);

  return text.slice(0, section.start) + updated + text.slice(section.end);
}

// 되돌릴 수 있게 백업용 파일 만들기
function backupWindowMode(current: number) {
  const backupPath = path.join(app.getPath('userData'), 'display-mode-backup.json');
  fs.writeFileSync(backupPath, JSON.stringify({ windowMode: current, changedAt: Date.now() }));
}

// 자동 테두리 없음 모드로 설정하기
export function ensureBorderlessMode() {
  const phase = getLcuState().phase;
  if (IN_GAME.includes(phase)) return;

  const configPath = findGameConfigPath();
  if (!configPath) return;

  try {
    const text = fs.readFileSync(configPath, 'utf8');
    const current = readWindowMode(text);
    if (current === null || current === BORDERLESS || current === WINDOWED) return;

    backupWindowMode(current);
    fs.writeFileSync(configPath, withWindowMode(text, BORDERLESS), 'utf-8');
  } catch (error) {
    console.debug('화면 모드 변경 실패', error);
  }
}
