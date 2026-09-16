import './index.css';

import { createRoot } from 'react-dom/client';

import DesktopLangProvider from '../../components/DesktopLangProvider';
import App from './App';

createRoot(document.getElementById('root')!).render(
  <DesktopLangProvider>
    <App />
  </DesktopLangProvider>,
);
