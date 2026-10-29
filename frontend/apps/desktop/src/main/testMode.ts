import { app } from 'electron';
import { getTestEnv, loadTestEnvFromProcess } from './testEnv';

loadTestEnvFromProcess(app.isPackaged);

const userDataDir = getTestEnv()?.userDataDir;
if (userDataDir) app.setPath('userData', userDataDir);
