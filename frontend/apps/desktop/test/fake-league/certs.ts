import fs from 'node:fs';
import path from 'node:path';

const CERT_DIR = path.join(__dirname, 'certs');

export const FAKE_CERT_PATH = path.join(CERT_DIR, 'cert.pem');
export const FAKE_CERT = fs.readFileSync(FAKE_CERT_PATH, 'utf-8');
export const FAKE_KEY_PATH = path.join(CERT_DIR, 'key.pem');
export const FAKE_KEY = fs.readFileSync(FAKE_KEY_PATH, 'utf-8');
