package dfgg.application.recommend.v3.feature;

import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.ScoredItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dfgg.application.recommend.v3.CandidateSource.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 후보 자체에서 바로 나오는 feature만 검증한다.
 * 통계 조회가 필요한 feature는 {@code StatsFeatureExtractorTest}에서 실 DB로 확인한다.
 */
class CandidateFeatureExtractorTest {

    private static final long INFINITY_EDGE = 3031L;
    private static final long LIANDRY = 6653L;

    private final CandidateFeatureExtractor extractor = new CandidateFeatureExtractor();

    private ItemCandidate candidateFrom(List<GeneratorResult> results, long itemId) {
        return CandidateUnion.merge(results).candidateOf(itemId);
    }

    @Test
    @DisplayName("어느 generator가 찾았는지를 0/1로 남긴다")
    void extract_WhenFoundByGenerators_SetsSourceFlags() {
        // given: build와 counter만 찾았다
        ItemCandidate candidate = candidateFrom(List.of(
                GeneratorResult.of(BUILD, List.of(new ScoredItem(INFINITY_EDGE, 0.8))),
                GeneratorResult.of(COUNTER, List.of(new ScoredItem(INFINITY_EDGE, 0.2)))
        ), INFINITY_EDGE);

        // when
        FeatureVector vector = FeatureVector.empty();
        extractor.extract(candidate, vector);

        // then
        assertThat(vector.get(FeatureName.SOURCE_BUILD)).isEqualTo(1.0);
        assertThat(vector.get(FeatureName.SOURCE_COUNTER)).isEqualTo(1.0);
        assertThat(vector.get(FeatureName.SOURCE_SELF_SYNERGY)).isZero();
        assertThat(vector.get(FeatureName.SOURCE_ALLY_SYNERGY)).isZero();
    }

    @Test
    @DisplayName("source별 score와 rank를 그대로 옮긴다 — generator가 매긴 근거가 손실 없이 전달된다")
    void extract_WhenCandidateHasEvidence_CopiesScoreAndRank() {
        // given: build 3위 0.81, self 1위 0.93
        ItemCandidate candidate = candidateFrom(List.of(
                GeneratorResult.of(BUILD, List.of(
                        new ScoredItem(6672L, 0.95), new ScoredItem(LIANDRY, 0.90),
                        new ScoredItem(INFINITY_EDGE, 0.81))),
                GeneratorResult.of(SELF_SYNERGY, List.of(new ScoredItem(INFINITY_EDGE, 0.93)))
        ), INFINITY_EDGE);

        // when
        FeatureVector vector = FeatureVector.empty();
        extractor.extract(candidate, vector);

        // then
        assertThat(vector.get(FeatureName.BUILD_SCORE)).isEqualTo(0.81);
        assertThat(vector.get(FeatureName.BUILD_RANK)).isEqualTo(3.0);
        assertThat(vector.get(FeatureName.SELF_SYNERGY_SCORE)).isEqualTo(0.93);
        assertThat(vector.get(FeatureName.SELF_SYNERGY_RANK)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("찾지 못한 generator의 score·rank는 NaN이다 — 0점으로 평가한 것과 구분된다")
    void extract_WhenGeneratorDidNotFindIt_LeavesScoreAsNaN() {
        // given: counter는 이 아이템을 내지 않았다
        ItemCandidate candidate = candidateFrom(List.of(
                GeneratorResult.of(BUILD, List.of(new ScoredItem(INFINITY_EDGE, 0.8)))
        ), INFINITY_EDGE);

        // when
        FeatureVector vector = FeatureVector.empty();
        extractor.extract(candidate, vector);

        // then
        assertThat(vector.get(FeatureName.COUNTER_SCORE)).isNaN();
        assertThat(vector.get(FeatureName.COUNTER_RANK)).isNaN();
        // 다만 "찾지 않았다"는 사실 자체는 0으로 명시된다
        assertThat(vector.get(FeatureName.SOURCE_COUNTER)).isZero();
    }

    @Test
    @DisplayName("counter가 0점으로 평가한 경우는 NaN이 아니라 0이다 — 이 구분이 실패 유형 분석의 전제다")
    void extract_WhenCounterScoredZero_IsZeroNotNaN() {
        // given
        ItemCandidate candidate = candidateFrom(List.of(
                GeneratorResult.of(COUNTER, List.of(new ScoredItem(INFINITY_EDGE, 0.0)))
        ), INFINITY_EDGE);

        // when
        FeatureVector vector = FeatureVector.empty();
        extractor.extract(candidate, vector);

        // then
        assertThat(vector.get(FeatureName.COUNTER_SCORE)).isZero().isNotNaN();
        assertThat(vector.get(FeatureName.SOURCE_COUNTER)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("generator가 얼마나 백오프했는지를 남긴다 — 그 점수를 얼마나 믿을지의 근거다")
    void extract_WhenGeneratorBackedOff_RecordsBackoffLevel() {
        // given: build가 2단계(챔피언 전반)까지 물러섰다
        ItemCandidate candidate = candidateFrom(List.of(
                GeneratorResult.of(BUILD, List.of(new ScoredItem(INFINITY_EDGE, 0.5)), 2)
        ), INFINITY_EDGE);

        // when
        FeatureVector vector = FeatureVector.empty();
        extractor.extract(candidate, vector);

        // then
        assertThat(vector.get(FeatureName.BUILD_BACKOFF_LEVEL)).isEqualTo(2.0);
    }

    @Test
    @DisplayName("네 generator가 모두 찾으면 네 벌의 근거가 모두 남는다")
    void extract_WhenAllGeneratorsFoundIt_KeepsAllFourEvidences() {
        // given
        ItemCandidate candidate = candidateFrom(List.of(
                GeneratorResult.of(BUILD, List.of(new ScoredItem(INFINITY_EDGE, 0.8))),
                GeneratorResult.of(SELF_SYNERGY, List.of(new ScoredItem(INFINITY_EDGE, 0.7))),
                GeneratorResult.of(ALLY_SYNERGY, List.of(new ScoredItem(INFINITY_EDGE, 0.6))),
                GeneratorResult.of(COUNTER, List.of(new ScoredItem(INFINITY_EDGE, 0.5)))
        ), INFINITY_EDGE);

        // when
        FeatureVector vector = FeatureVector.empty();
        extractor.extract(candidate, vector);

        // then
        assertThat(vector.get(FeatureName.BUILD_SCORE)).isEqualTo(0.8);
        assertThat(vector.get(FeatureName.SELF_SYNERGY_SCORE)).isEqualTo(0.7);
        assertThat(vector.get(FeatureName.ALLY_SYNERGY_SCORE)).isEqualTo(0.6);
        assertThat(vector.get(FeatureName.COUNTER_SCORE)).isEqualTo(0.5);
    }
}
