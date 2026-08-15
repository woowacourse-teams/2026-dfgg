import { BrowserRouter, Route, Routes } from 'react-router-dom';

import ChampionSelect from './pages/champion-select/ChampionSelect';
import Home from './pages/home/Home';
// import Nickname from './pages/nickname/Nickname';
import Privacy from './pages/privacy/Privacy';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Home />} />
        {/* <Route path="/nickname" element={<Nickname />} /> */}
        <Route path="/champion-select" element={<ChampionSelect />} />
        <Route path="/privacy" element={<Privacy />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
