package dfgg.application.recommend.v3.ranker;

import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.SourceEvidence;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * <b>임시 랭커.</b> LambdaMART(T12)가 들어오면 통째로 교체된다.
 *
 * <p>지금은 generator가 하나(Build)뿐이라 최종 순위를 논할 대상이 없다. 파이프라인이 요청부터
 * Top-5까지 실제로 흐르는지 확인하려고 자리만 잡아둔 구현이며, source별 점수를 섞거나 가중치를
 * 주지 않는다 — 가장 높은 점수 하나를 그대로 쓴다. 여기에 가중치를 넣기 시작하면 이번 작업이
 * 없애려는 수동 랭킹이 되살아난다.
 */
@Component
public class BestSourceScoreRanker implements CandidateRanker {

    public static final String MODEL_VERSION = "best-source-score-skeleton";

    @Override
    public List<Long> rank(CandidateUnion union, RecommendationQuery query, int topN) {
        return union.candidates().stream()
                .sorted(Comparator.comparingDouble(this::bestScore).reversed()
                        .thenComparing(ItemCandidate::itemId))
                .limit(topN)
                .map(ItemCandidate::itemId)
                .toList();
    }

    @Override
    public String modelVersion() {
        return MODEL_VERSION;
    }

    private double bestScore(ItemCandidate candidate) {
        return candidate.sources().stream()
                .map(candidate::evidenceOf)
                .flatMap(java.util.Optional::stream)
                .mapToDouble(SourceEvidence::score)
                .max()
                .orElse(0.0);
    }
}
