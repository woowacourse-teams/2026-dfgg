import { BrowserRouter, Route, Routes } from 'react-router-dom';

import SiteFooter from './components/SiteFooter';
import TopBar from './components/TopBar';
import ChampionSelect from './pages/champion-select/ChampionSelect';
import DesktopApp from './pages/DesktopApp/DesktopApp';
import Feedback from './pages/feedback/Feedback';
import Home from './pages/home/Home';
// import Nickname from './pages/nickname/Nickname';
import Privacy from './pages/privacy/Privacy';

function App() {
  return (
    <BrowserRouter>
      <TopBar />

      <div className='min-h-screen bg-ground'>
        <div className='mx-auto max-w-250 px-6 pt-11 pb-13'>
          <Routes>
            <Route path='/' element={<Home />} />
            {/* <Route path="/nickname" element={<Nickname />} /> */}
            <Route path='desktop-app' element={<DesktopApp />} />
            <Route path='/feedback' element={<Feedback />} />
            <Route path='/champion-select' element={<ChampionSelect />} />
            <Route path='/privacy' element={<Privacy />} />
          </Routes>
        </div>
      </div>
      <div className='mx-auto max-w-250 px-6 pt-11 pb-13'>
        <SiteFooter />
      </div>
    </BrowserRouter>
  );
}

export default App;
