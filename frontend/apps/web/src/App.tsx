// 앱 진입 파일
import { stripLocale } from './i18n/i18n';
import Features from './pages/features/Features';
import Home from './pages/home/Home';
import Privacy from './pages/privacy/Privacy';

function App() {
  const path = stripLocale(window.location.pathname);
  if (path.startsWith('/privacy')) return <Privacy />;
  if (path.startsWith('/features')) return <Features />;
  return <Home />;
}

export default App;
