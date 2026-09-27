// 앱 진입 파일
import Home from './pages/home/Home';
import Privacy from './pages/privacy/Privacy';

function App() {
  return window.location.pathname.startsWith('/privacy') ? <Privacy /> : <Home />;
}

export default App;
