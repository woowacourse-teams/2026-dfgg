import './HighlightPlayer.css';

import { useRef, useState } from 'react';

import type { HighlightGame } from '../../../../shared/types';
import { t } from '../../../i18n';

// clips.json의 file은 'clip-01.mp4'라서 clips/ 를 붙인다 (8-2의 dfgg-media 프로토콜)
export const clipUrl = (id: string, file: string) => `dfgg-media://clip/${id}/clips/${file}`;

// 16.4 → '0:16'
export const formatClock = (sec: number) => {
  const s = Math.round(sec);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
};

/**
 * 한 판의 클립을 보여주는 플레이어. 방금 끝난 판과 전적 카드에서 같이 쓴다.
 * 클립 고르는 줄은 클립 길이만큼 칸을 나눈 빗금 막대다. 지금 클립 칸은 재생한 만큼 차오른다.
 */
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
  const fillRef = useRef<HTMLSpanElement>(null);

  const total = game.clips.length;
  const clip = game.clips[clipIndex] ?? game.clips[0];
  const lengthOf = (index: number) => Math.max(1, game.clips[index].end - game.clips[index].start);

  const select = (index: number) => {
    setClipIndex(index);
    setPlaying(true);
  };

  const pick = (index: number, how: 'rail' | 'prev' | 'next') => {
    select(index);
    window.analytics.track('highlight-clip-select', { source, index: index + 1, total, how });
  };

  // 재생한 만큼 지금 칸을 채운다. 매 프레임 상태를 바꾸지 않고 transform 만 직접 건드린다
  const onTimeUpdate = (video: HTMLVideoElement) => {
    const ratio = video.duration ? video.currentTime / video.duration : 0;
    if (fillRef.current) fillRef.current.style.transform = `scaleX(${ratio})`;
  };

  return (
    <div className='hl'>
      <div className='hl-stage'>
        <video
          key={clip.file}
          className='hl-video'
          src={clipUrl(game.id, clip.file)}
          controls
          autoPlay={playing}
          onTimeUpdate={(event) => onTimeUpdate(event.currentTarget)}
          onEnded={() => {
            if (clipIndex + 1 < total) select(clipIndex + 1); // 쇼츠처럼 다음 클립
            window.analytics.track('highlight-complete', { source, total });
          }}
        />
        {/* 영상 위 코너 딱지. 오늘의 픽과 같은 중계 그래픽 모양 */}
        <span className='hl-tag' aria-hidden='true'>
          <b>HIGHLIGHT</b>
          <span>{t.highlight.clipOf(clipIndex + 1, total)}</span>
        </span>
      </div>

      {total > 1 && (
        <div className='hl-rail'>
          <button
            type='button'
            className='hl-step'
            onClick={() => pick(clipIndex - 1, 'prev')}
            disabled={clipIndex === 0}
            aria-label={t.highlight.prev}
          >
            ‹
          </button>

          <ol className='hl-segments' aria-label={t.highlight.clipsLabel}>
            {game.clips.map((c, index) => (
              <li key={c.file} style={{ flexGrow: lengthOf(index) }}>
                <button
                  type='button'
                  className={`hl-segment ${index === clipIndex ? 'hl-segment-on' : ''} ${
                    index < clipIndex ? 'hl-segment-done' : ''
                  }`}
                  aria-current={index === clipIndex ? 'true' : undefined}
                  aria-label={t.highlight.clipLabel(index + 1, formatClock(lengthOf(index)))}
                  onClick={() => pick(index, 'rail')}
                >
                  <span className='hl-segment-bar'>
                    {index === clipIndex && <span ref={fillRef} className='hl-segment-fill' />}
                  </span>
                  <span className='hl-segment-text'>
                    <b>{index + 1}</b>
                    {formatClock(lengthOf(index))}
                  </span>
                </button>
              </li>
            ))}
          </ol>

          <button
            type='button'
            className='hl-step'
            onClick={() => pick(clipIndex + 1, 'next')}
            disabled={clipIndex === total - 1}
            aria-label={t.highlight.next}
          >
            ›
          </button>
        </div>
      )}
    </div>
  );
}

export default HighlightPlayer;
