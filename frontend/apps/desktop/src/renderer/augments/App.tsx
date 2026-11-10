import { useEffect, useState } from 'react';

import type { MayhemSession } from '../../shared/types';

function App() {
  const [session, setSession] = useState<MayhemSession | null>(null);

  useEffect(() => {
    window.mayhem.getSession().then(setSession);
  }, []);

  if (!session) return null;

  return (
    <p
      style={{ position: 'fixed', top: 8, left: 8, color: '#fff', background: '#000a', padding: 4 }}
    >
      mayhem {session.championId}
    </p>
  );
}

export default App;
