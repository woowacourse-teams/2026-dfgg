import './HighlightSection.css';

import { useCallback, useEffect, useState } from 'react';

import type { HighlightGame, HighlightProgress } from '../../../../shared/types';

// clips.json의 file은 'clip-01.mp4'라서 clips/ 를 붙인다
const clipUrl = (id: string, file: string) => `dfgg-media://clip/${id}/clips/${file}`;

// 1791653466702 → '10/10 17:31'
const formatDate = (ms: number) => {
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
};

function HighlightSection() {
  const [games, setGames] = useState<HighlightGame[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [clipIndex, setClipIndex] = useState(0);
  const [progress, setProgress] = useState<HighlightProgress | null>(null);
  const [autoPlay, setAutoPlay] = useState(false); // 앱 켜자마자 재생되지 않게

  // 목록을 다시 받는다. selectId를 주면 그 판으로 이동
  const load = useCallback(async (selectId?: string) => {
    const list = await window.highlight.list();
    setGames(list);
    if (selectId) {
      setSelectedId(selectId);
      setClipIndex(0);
    }
  }, []);

  useEffect(() => {
    window.highlight.list().then(setGames);

    // 게임이 끝나 클립을 만드는 동안 진행률을 받는다
    return window.highlight.onProgress((p) => {
      if (!p.finished) {
        setProgress(p);
        return;
      }
      setProgress(null);
      setAutoPlay(true);
      // 다 만들어지면 목록을 다시 받고 방금 판으로 이동
      window.highlight.list().then((list) => {
        setGames(list);
        setSelectedId(p.id);
        setClipIndex(0);
      });
    });
  }, [load]);

  const selectGame = (id: string) => {
    setSelectedId(id);
    setClipIndex(0);
    setAutoPlay(true);
  };

  const selectClip = (i: number) => {
    setClipIndex(i);
    setAutoPlay(true);
  };

  // 선택한 판이 없거나 목록에서 사라졌으면 최신 판
  const game = games.find((g) => g.id === selectedId) ?? games[0];
  const clip = game?.clips[clipIndex];

  if (!progress && !game) return null; // 보여줄 게 없으면 섹션 숨김

  return (
    <section className='highlights'>
      <h2 className='highlights-title'>하이라이트</h2>

      {progress && (
        <p className='highlights-progress'>
          이번 판 하이라이트 만드는 중... {progress.done}/{progress.total}
        </p>
      )}

      {game && clip && (
        <>
          <video
            className='highlights-video'
            src={clipUrl(game.id, clip.file)}
            controls
            autoPlay={autoPlay}
            onEnded={() => {
              if (clipIndex + 1 < game.clips.length) selectClip(clipIndex + 1);
            }}
          />

          <div className='highlights-clips'>
            {game.clips.map((c, i) => (
              <button
                key={c.file}
                className={i === clipIndex ? 'active' : ''}
                onClick={() => selectClip(i)}
              >
                {i + 1}
              </button>
            ))}
          </div>

          {games.length > 1 && (
            <div className='highlights-games'>
              {games.map((g) => (
                <button
                  key={g.id}
                  className={g.id === game.id ? 'active' : ''}
                  onClick={() => selectGame(g.id)}
                >
                  {formatDate(g.startAt)} · {g.clips.length}개
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}

export default HighlightSection;
