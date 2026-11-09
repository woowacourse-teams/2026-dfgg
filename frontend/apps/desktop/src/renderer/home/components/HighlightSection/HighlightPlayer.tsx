import './HighlightPlayer.css';

import { useState } from 'react';

import type { HighlightGame } from '../../../../shared/types';

// clips.json의 file은 'clip-01.mp4'라서 clips/ 를 붙인다 (8-2의 dfgg-media 프로토콜)
export const clipUrl = (id: string, file: string) => `dfgg-media://clip/${id}/clips/${file}`;

// 한 판의 클립을 보여주는 플레이어. 하이라이트 섹션과 전적 카드에서 같이 쓴다
function HighlightPlayer({
  game,
  autoPlay = false,
  source,
}: {
  game: HighlightGame;
  autoPlay?: boolean;
  source: string;
}) {
  const [clipIndex, setClipIndex] = useState(0);
  const [playing, setPlaying] = useState(autoPlay); // 클립을 고른 뒤부터는 자동재생

  const clip = game.clips[clipIndex] ?? game.clips[0];
  const total = game.clips.length;

  const select = (i: number) => {
    setClipIndex(i);
    setPlaying(true);
  };

  return (
    <div className='hl-player'>
      <video
        className='hl-video'
        src={clipUrl(game.id, clip.file)}
        controls
        autoPlay={playing}
        onEnded={() => {
          if (clipIndex + 1 < game.clips.length) select(clipIndex + 1); // 쇼츠처럼 다음 클립
          window.analytics.track('highlight-complete', { source, total });
        }}
      />

      {game.clips.length > 1 && (
        <div className='hl-clips'>
          {game.clips.map((c, i) => (
            <button
              key={c.file}
              type='button'
              className={i === clipIndex ? 'active' : ''}
              onClick={() => {
                select(i);
                window.analytics.track('highlight-clip-select', { source, index: i + 1, total });
              }}
            >
              {i + 1}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export default HighlightPlayer;
