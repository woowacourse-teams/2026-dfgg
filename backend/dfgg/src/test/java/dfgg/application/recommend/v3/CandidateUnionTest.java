package dfgg.application.recommend.v3;

import static dfgg.application.recommend.v3.CandidateSource.ALLY_SYNERGY;
import static dfgg.application.recommend.v3.CandidateSource.BUILD;
import static dfgg.application.recommend.v3.CandidateSource.COUNTER;
import static dfgg.application.recommend.v3.CandidateSource.SELF_SYNERGY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandidateUnionTest {

    private static final long INFINITY_EDGE = 3031L;
    private static final long KRAKEN_SLAYER = 6672L;
    private static final long LIANDRY = 6653L;

    @Test
    @DisplayName("여러 generator가 같은 아이템을 내면 하나로 합치되 source별 score와 rank를 전부 보존한다")
    void merge_WhenItemFoundByMultipleGenerators_PreservesEverySourceScoreAndRank() {
        // given: 무한의 대검이 build 3위(0.81), self 1위(0.93)로 발견됨
        GeneratorResult build = GeneratorResult.of(BUILD, List.of(
                new ScoredItem(KRAKEN_SLAYER, 0.95),
                new ScoredItem(LIANDRY, 0.90),
                new ScoredItem(INFINITY_EDGE, 0.81)
        ));
        GeneratorResult self = GeneratorResult.of(SELF_SYNERGY, List.of(
                new ScoredItem(INFINITY_EDGE, 0.93)
        ));

        // when
        CandidateUnion union = CandidateUnion.merge(List.of(build, self));

        // then
        ItemCandidate candidate = union.candidateOf(INFINITY_EDGE);
        assertThat(candidate.evidenceOf(BUILD)).contains(new SourceEvidence(0.81, 3));
        assertThat(candidate.evidenceOf(SELF_SYNERGY)).contains(new SourceEvidence(0.93, 1));
    }

    @Test
    @DisplayName("같은 아이템이 여러 generator에서 나와도 후보는 하나로 합쳐진다")
    void merge_WhenSameItemFromMultipleGenerators_ProducesSingleCandidate() {
        // given
        GeneratorResult build = GeneratorResult.of(BUILD, List.of(new ScoredItem(INFINITY_EDGE, 0.8)));
        GeneratorResult self = GeneratorResult.of(SELF_SYNERGY, List.of(new ScoredItem(INFINITY_EDGE, 0.9)));
        GeneratorResult counter = GeneratorResult.of(COUNTER, List.of(new ScoredItem(INFINITY_EDGE, 0.1)));

        // when
        CandidateUnion union = CandidateUnion.merge(List.of(build, self, counter));

        // then
        assertThat(union.candidates()).hasSize(1);
        assertThat(union.candidateOf(INFINITY_EDGE).sources())
                .containsExactlyInAnyOrder(BUILD, SELF_SYNERGY, COUNTER);
    }

    @Test
    @DisplayName("발견되지 않은 source는 '결측'이며, 점수 0.0으로 발견된 것과 구분된다")
    void merge_WhenSourceDidNotFindItem_IsMissingAndDistinctFromScoreZero() {
        // given: counter가 리안드리는 0.0점으로 '발견'했고, 무한의 대검은 아예 내지 않았다
        GeneratorResult build = GeneratorResult.of(BUILD, List.of(
                new ScoredItem(INFINITY_EDGE, 0.8),
                new ScoredItem(LIANDRY, 0.7)
        ));
        GeneratorResult counter = GeneratorResult.of(COUNTER, List.of(
                new ScoredItem(LIANDRY, 0.0)
        ));

        // when
        CandidateUnion union = CandidateUnion.merge(List.of(build, counter));

        // then: 0.0으로 발견된 쪽은 존재하고, 발견 안 된 쪽은 비어있다
        assertThat(union.candidateOf(LIANDRY).evidenceOf(COUNTER))
                .contains(new SourceEvidence(0.0, 1));
        assertThat(union.candidateOf(INFINITY_EDGE).evidenceOf(COUNTER))
                .isEmpty();
        assertThat(union.candidateOf(INFINITY_EDGE).hasSource(COUNTER)).isFalse();
    }

    @Test
    @DisplayName("generator 순서를 바꿔도 union 결과가 완전히 동일하다")
    void merge_WhenGeneratorOrderChanges_ProducesIdenticalUnion() {
        // given
        GeneratorResult build = GeneratorResult.of(BUILD, List.of(new ScoredItem(INFINITY_EDGE, 0.8)));
        GeneratorResult self = GeneratorResult.of(SELF_SYNERGY, List.of(new ScoredItem(KRAKEN_SLAYER, 0.9)));
        GeneratorResult ally = GeneratorResult.of(ALLY_SYNERGY, List.of(new ScoredItem(LIANDRY, 0.7)));
        GeneratorResult counter = GeneratorResult.of(COUNTER, List.of(new ScoredItem(INFINITY_EDGE, 0.6)));

        // when
        CandidateUnion forward = CandidateUnion.merge(List.of(build, self, ally, counter));
        CandidateUnion reversed = CandidateUnion.merge(List.of(counter, ally, self, build));

        // then
        assertThat(reversed).isEqualTo(forward);
        assertThat(reversed.candidates()).containsExactlyElementsOf(forward.candidates());
    }

    @Test
    @DisplayName("모든 generator가 빈 결과를 내면 빈 union이 된다")
    void merge_WhenEveryGeneratorIsEmpty_ProducesEmptyUnion() {
        // given
        List<GeneratorResult> empties = List.of(
                GeneratorResult.of(BUILD, List.of()),
                GeneratorResult.of(SELF_SYNERGY, List.of()),
                GeneratorResult.of(ALLY_SYNERGY, List.of()),
                GeneratorResult.of(COUNTER, List.of())
        );

        // when
        CandidateUnion union = CandidateUnion.merge(empties);

        // then
        assertThat(union.isEmpty()).isTrue();
        assertThat(union.candidates()).isEmpty();
    }

    @Test
    @DisplayName("후보 목록은 아이템 ID 오름차순으로 결정적인 순서를 가진다")
    void candidates_WhenMerged_AreOrderedByItemIdForDeterminism() {
        // given
        GeneratorResult build = GeneratorResult.of(BUILD, List.of(
                new ScoredItem(KRAKEN_SLAYER, 0.9),
                new ScoredItem(LIANDRY, 0.8),
                new ScoredItem(INFINITY_EDGE, 0.7)
        ));

        // when
        CandidateUnion union = CandidateUnion.merge(List.of(build));

        // then
        assertThat(union.candidates())
                .extracting(ItemCandidate::itemId)
                .containsExactly(INFINITY_EDGE, LIANDRY, KRAKEN_SLAYER);
    }

    @Test
    @DisplayName("generator가 얼마나 백오프했는지도 union을 통과해 살아남는다 — 그 점수를 얼마나 믿을지의 근거다")
    void merge_WhenGeneratorBackedOff_PreservesBackoffLevel() {
        // given: build는 2단계까지 물러섰고, self는 백오프하지 않았다
        GeneratorResult backedOffBuild = GeneratorResult.of(
                BUILD, List.of(new ScoredItem(INFINITY_EDGE, 0.5)), 2);
        GeneratorResult freshSelf = GeneratorResult.of(
                SELF_SYNERGY, List.of(new ScoredItem(INFINITY_EDGE, 0.9)));

        // when
        CandidateUnion union = CandidateUnion.merge(List.of(backedOffBuild, freshSelf));

        // then
        ItemCandidate candidate = union.candidateOf(INFINITY_EDGE);
        assertThat(candidate.evidenceOf(BUILD).orElseThrow().backoffLevel()).isEqualTo(2);
        assertThat(candidate.evidenceOf(SELF_SYNERGY).orElseThrow().backoffLevel()).isZero();
    }

    @Test
    @DisplayName("같은 source의 결과가 두 번 들어오면 거부한다 — 조용히 덮어쓰면 배선 버그가 드러나지 않는다")
    void merge_WhenSameSourceAppearsTwice_ThrowsException() {
        // given
        GeneratorResult first = GeneratorResult.of(BUILD, List.of(new ScoredItem(INFINITY_EDGE, 0.8)));
        GeneratorResult duplicateSource = GeneratorResult.of(BUILD, List.of(new ScoredItem(LIANDRY, 0.7)));

        // when & then
        assertThatThrownBy(() -> CandidateUnion.merge(List.of(first, duplicateSource)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUILD");
    }

    @Test
    @DisplayName("한 generator만 후보를 내도 union이 만들어진다")
    void merge_WhenOnlyOneGeneratorHasCandidates_StillProducesUnion() {
        // given
        GeneratorResult counter = GeneratorResult.of(COUNTER, List.of(new ScoredItem(LIANDRY, 0.5)));

        // when
        CandidateUnion union = CandidateUnion.merge(List.of(
                GeneratorResult.of(BUILD, List.of()), counter
        ));

        // then
        assertThat(union.candidates()).hasSize(1);
        assertThat(union.candidateOf(LIANDRY).sources()).containsExactly(COUNTER);
    }
}
