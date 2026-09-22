import { app } from 'electron';

const BE_PROD = 'http://13.125.209.101:8080';
// const BE_DEV = 'http://3.36.100.9:8080';

export function getApiBaseUrl() {
  if (app.isPackaged) return BE_PROD;
  return process.env.API_TARGET ?? BE_PROD;
}
