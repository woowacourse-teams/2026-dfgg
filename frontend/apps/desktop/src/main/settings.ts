import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import { mainLocale } from './locale';
import type { Locale } from '../shared/i18n';

export type Settings = {
  locale?: Locale;
  docking?: boolean;
  showOverlay?: boolean;
};

const file = path.join(app.getPath('userData'), 'setting.json');

export function readSettings(): Settings {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf-8'));
  } catch {
    return {};
  }
}

export function getLocale(): Locale {
  return mainLocale();
}

export function updateSettings(patch: Partial<Settings>) {
  fs.writeFileSync(file, JSON.stringify({ ...readSettings(), ...patch }));
}
