// 앱 진입 파일
import { stripLocale } from './i18n/i18n';
import Home from './pages/home/Home';
import Privacy from './pages/privacy/Privacy';

function App() {
  return stripLocale(window.location.pathname).startsWith('/privacy') ? <Privacy /> : <Home />;
}

export default App;
