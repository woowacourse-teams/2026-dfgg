import { app } from 'electron';
import fs from 'node:fs';
import path from 'node:path';
import { mainLocale } from './locale';
import type { Locale } from '../shared/i18n';

const file = path.join(app.getPath('userData'), 'setting.json');

export function readSettings(): { locale?: Locale } {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf-8'));
  } catch {
    return {};
  }
}

export function getLocale(): Locale {
  return mainLocale();
}

export function setLocale(locale: Locale) {
  fs.writeFileSync(file, JSON.stringify({ ...readSettings(), locale }));
}
