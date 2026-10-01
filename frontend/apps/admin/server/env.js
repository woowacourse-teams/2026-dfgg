const fs = require('fs');
const path = require('path');

// apps/admin/.env 를 읽는다. 키는 이 프로세스(Node)에만 있고 브라우저 번들에는 들어가지 않는다.
const ENV_PATH = path.resolve(__dirname, '../.env');

function loadEnv() {
  if (fs.existsSync(ENV_PATH)) process.loadEnvFile(ENV_PATH);
}

function env(name, fallback) {
  const value = process.env[name];
  return value === undefined || value === '' ? fallback : value;
}

// .env 설정이 빠져서 난 오류. 화면에서 설정 안내를 보여줄 때 구분한다.
class SetupError extends Error {}

module.exports = { loadEnv, env, ENV_PATH, SetupError };
