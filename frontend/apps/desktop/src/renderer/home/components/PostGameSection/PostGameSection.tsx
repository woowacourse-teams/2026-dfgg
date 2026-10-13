import './PostGameSection.css';

import { useEffect, useState } from 'react';

import type {
  GameflowPhase,
  HighlightGame,
  HighlightProgress,
  MatchSummary,
} from '../../../../shared/types';
import HighlightPlayer from '../HighlightSection/HighlightPlayer';
import { MatchCard } from '../MatchSection/MatchSection';

// 다음 판을 시작하면 방금 판 카드는 숨긴다
const NEXT_GAME_PHASES: (GameflowPhase | null)[] = ['ChampSelect', 'GameStart', 'InProgress'];

function PostGameSection({
  matches,
  version,
  phase,
}: {
  matches: MatchSummary[] | null;
  version: string | null;
  phase: GameflowPhase | null;
}) {
  const [progress, setProgress] = useState<HighlightProgress | null>(null); // 만드는 중
  const [game, setGame] = useState<HighlightGame | null>(null); // 다 만든 방금 판
  const [closed, setClosed] = useState(false); // 사용자가 ✕를 눌렀는지

  useEffect(
    () =>
      window.highlight.onProgress((p) => {
        setClosed(false); // 새 판이 끝나면 다시 보여준다
        if (!p.finished) {
          setProgress(p);
          return;
        }
        setProgress(null);
        // 세션 id(p.id)로 방생 판을 찾는다. 클립이 0개면 목록에 없어서 null → 카드 안 뛸
        window.highlight.list().then((list) => {
          const found = list.find((g) => g.id === p.id) ?? null;
          setGame(found);
          if (found)
            window.analytics.track('postgame-highlight-show', {
              clips: found.clips.length,
              type: '방금 판 하이라이트가 화면에 뜰 때',
            });
        });
      }),
    [],
  );

  // 방금 판의 전적 (9-1의 gameId로 연결). 전적 반영 전이면 undefined
  const match = game?.gameId ? matches?.find((m) => m.gameId === game.gameId) : undefined;

  if (closed || NEXT_GAME_PHASES.includes(phase)) return null;
  if (!progress && !game) return null;

  return (
    <section className='postgame'>
      <div className='section-head'>
        <h1 className='section-title'>방금 끝난 판</h1>
        <button
          type='button'
          className='postgame-close'
          onClick={() => {
            setClosed(true);
            window.analytics.track('postgame-close', {
              clips: game?.clips.length ?? 0,
              making: progress !== null,
            });
          }}
          aria-label='닫기'
        >
          ✕
        </button>
      </div>

      {progress && (
        <p className='postgame-progress'>
          하이라이트 만드는 중... {progress.done}/{progress.total}
        </p>
      )}

      {game && (
        <>
          {match ? (
            <ul className='match-list'>
              <MatchCard match={match} version={version} />
            </ul>
          ) : (
            <p className='postgame-note'>전적에 반영되는 중이에요</p>
          )}
          <div className='postgame-player'>
            <HighlightPlayer key={game.id} game={game} autoPlay source='postgame' />
          </div>
        </>
      )}
    </section>
  );
}

export default PostGameSection;
