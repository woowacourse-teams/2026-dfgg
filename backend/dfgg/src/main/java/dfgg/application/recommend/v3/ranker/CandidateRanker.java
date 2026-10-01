package dfgg.application.recommend.v3.ranker;

import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.RecommendationQuery;
import java.util.List;

/**
 * 후보의 최종 순위를 <b>단독으로</b> 결정한다.
 *
 * <p>generator는 서로 다른 이유로 후보를 발견할 뿐이고, 그 근거들 사이의 trade-off는
 * 전부 여기서 정해진다. 수동 가중합·context별 boost·counter override는 두지 않는다.
 */
public interface CandidateRanker {

    List<RankedCandidate> rank(CandidateUnion union, RecommendationQuery query, int topN);

    /** 어떤 랭커가 이 순위를 냈는지. 응답에 담겨 운영 중 관측 지표가 된다. */
    String modelVersion();
}
