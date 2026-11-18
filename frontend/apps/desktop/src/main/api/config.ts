import { app } from 'electron';
import { getTestEnv } from '../testEnv';

const BE_PROD = 'http://43.203.222.190:8080';
// const BE_DEV = 'http://3.36.100.9:8080';

export function getApiBaseUrl() {
  if (app.isPackaged) return BE_PROD;
  return getTestEnv()?.apiUrl ?? process.env.API_TARGET ?? BE_PROD;
}
