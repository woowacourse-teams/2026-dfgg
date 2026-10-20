import './DfRankCard.css';

import type { DfScore } from '../../../../shared/types';
import { t } from '../../../i18n';
import { rankTitle } from '../../lib/dfRank';

const TOP_RANKS = 3;

type Props = {
  score: DfScore;
  /** 이 판에 점수가 매겨진 사람 수 (보통 10) */
  total: number;
};

/** 방금 끝난 판 카드 가운데에 들어가는 내 DF 스코어 등수. 색은 감싸는 쪽의 df-tone-* 를 따른다. */
function DfRankCard({ score, total }: Props) {
  return (
    <span className='df-card-body'>
      <span className='df-card-label'>{t.df.cardLabel}</span>
      <strong className='df-card-title'>{rankTitle(score.rank)}</strong>
      <span className='df-card-meta'>
        {t.df.meta(score.score.toFixed(1), total)}
        {/* 4등부터는 제목이 이미 'N등'이라 다시 적지 않는다 */}
        {score.rank <= TOP_RANKS && ` ${t.df.rank(score.rank)}`}
      </span>

      {/* 1등부터 꼴찌까지 칸을 늘어놓고 내 칸만 칠한다 */}
      <span className='df-card-ladder' aria-hidden='true'>
        {Array.from({ length: total }, (_, index) => (
          <i key={index} className={index + 1 === score.rank ? 'df-card-here' : ''} />
        ))}
      </span>
    </span>
  );
}

export default DfRankCard;
