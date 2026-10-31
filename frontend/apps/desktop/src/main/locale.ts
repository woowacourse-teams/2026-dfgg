import { app } from 'electron';
import { pickLocale, strings } from '../shared/i18n';
import { readSettings } from './settings';

// app ready 이후에 불러야 한다. 그 전에는 getLocale() 이 빈 값이라 늘 영어가 된다.
export const mainLocale = () => readSettings().locale ?? pickLocale([app.getLocale()]);
export const mainStrings = () => strings(mainLocale());
