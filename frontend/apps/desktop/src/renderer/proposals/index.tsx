import './index.css';
import '../home/App.css';
import '../home/components/MatchSection/MatchSection.css';
import '../home/components/MatchSection/MatchDetail.css';
import './proposals.css';
import './champSelect.css';
import './counter.css';

import ReactDOM from 'react-dom/client';

import Proposals from './Proposals';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(<Proposals />);
