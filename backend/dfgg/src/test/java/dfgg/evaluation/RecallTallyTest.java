package dfgg.evaluation;

import static dfgg.application.recommend.v3.CandidateSource.ALLY_SYNERGY;
import static dfgg.application.recommend.v3.CandidateSource.BUILD;
import static dfgg.application.recommend.v3.CandidateSource.COUNTER;
import static dfgg.application.recommend.v3.CandidateSource.SELF_SYNERGY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecallTallyTest {

    private static final long GROUND_TRUTH = 3031L;

    @Test
    @DisplayName("정답이 상위 K 안에 있으면 hit으로 센다")
    void recallAt_WhenGroundTruthIsWithinTopK_CountsAsHit() {
        // given: 정답이 3위
        RecallTally tally = new RecallTally();
        tally.record(BUILD, List.of(6673L, 3006L, GROUND_TRUTH, 3072L), GROUND_TRUTH);

        // when & then
        assertThat(tally.recallAt(BUILD, 3)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("정답이 K보다 뒤에 있으면 miss다 — K를 정하는 근거가 여기서 나온다")
    void recallAt_WhenGroundTruthIsBeyondK_CountsAsMiss() {
        // given: 정답이 4위인데 K=3
        RecallTally tally = new RecallTally();
        tally.record(BUILD, List.of(6673L, 3006L, 3072L, GROUND_TRUTH), GROUND_TRUTH);

        // when & then
        assertThat(tally.recallAt(BUILD, 3)).isZero();
    }

    @Test
    @DisplayName("정답이 후보에 아예 없으면 miss다")
    void recallAt_WhenGroundTruthIsAbsent_CountsAsMiss() {
        // given
        RecallTally tally = new RecallTally();
        tally.record(BUILD, List.of(6673L, 3006L), GROUND_TRUTH);

        // when & then
        assertThat(tally.recallAt(BUILD, 100)).isZero();
    }

    @Test
    @DisplayName("여러 query에 걸쳐 hit 비율을 낸다")
    void recallAt_WhenMultipleQueries_AveragesHitRate() {
        // given: 4개 query 중 3개가 hit
        RecallTally tally = new RecallTally();
        tally.record(BUILD, List.of(GROUND_TRUTH), GROUND_TRUTH);
        tally.record(BUILD, List.of(GROUND_TRUTH), GROUND_TRUTH);
        tally.record(BUILD, List.of(GROUND_TRUTH), GROUND_TRUTH);
        tally.record(BUILD, List.of(6673L), GROUND_TRUTH);

        // when & then
        assertThat(tally.recallAt(BUILD, 10)).isCloseTo(0.75, within(1e-9));
    }

    @Test
    @DisplayName("union recall은 어느 generator든 하나라도 찾으면 hit이다")
    void unionRecallAt_WhenAnyGeneratorFindsIt_CountsAsHit() {
        // given: Build는 놓쳤지만 Counter가 찾았다
        RecallTally tally = new RecallTally();
        tally.record(Map.of(
                BUILD, List.of(6673L, 3006L),
                COUNTER, List.of(GROUND_TRUTH)
        ), GROUND_TRUTH);

        // when & then
        assertThat(tally.unionRecallAt(10)).isEqualTo(1.0);
        assertThat(tally.recallAt(BUILD, 10)).isZero();
        assertThat(tally.recallAt(COUNTER, 10)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("union recall은 개별 generator recall보다 낮을 수 없다 — union의 목적이다")
    void unionRecallAt_IsNeverLowerThanAnySingleGenerator() {
        // given
        RecallTally tally = new RecallTally();
        tally.record(Map.of(BUILD, List.of(GROUND_TRUTH), COUNTER, List.of(6673L)), GROUND_TRUTH);
        tally.record(Map.of(BUILD, List.of(6673L), COUNTER, List.of(GROUND_TRUTH)), GROUND_TRUTH);

        // when & then
        assertThat(tally.unionRecallAt(10)).isEqualTo(1.0);
        assertThat(tally.recallAt(BUILD, 10)).isCloseTo(0.5, within(1e-9));
        assertThat(tally.recallAt(COUNTER, 10)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("그 generator만 찾은 정답의 비율을 센다 — generator를 유지할 가치가 여기서 드러난다")
    void uniqueContributionOf_WhenOnlyOneGeneratorFindsIt_CountsIt() {
        // given: Counter만 찾은 query 1개, Build도 함께 찾은 query 1개
        RecallTally tally = new RecallTally();
        tally.record(Map.of(BUILD, List.of(6673L), COUNTER, List.of(GROUND_TRUTH)), GROUND_TRUTH);
        tally.record(Map.of(BUILD, List.of(GROUND_TRUTH), COUNTER, List.of(GROUND_TRUTH)), GROUND_TRUTH);

        // when & then: Counter 단독 기여는 2건 중 1건
        assertThat(tally.uniqueContributionOf(COUNTER, 10)).isCloseTo(0.5, within(1e-9));
        assertThat(tally.uniqueContributionOf(BUILD, 10)).isZero();
    }

    @Test
    @DisplayName("아무 generator도 못 찾으면 union recall이 0이다 — LTR이 넘을 수 없는 천장")
    void unionRecallAt_WhenNoGeneratorFindsIt_IsZero() {
        // given
        RecallTally tally = new RecallTally();
        tally.record(Map.of(
                BUILD, List.of(6673L), SELF_SYNERGY, List.of(3006L),
                ALLY_SYNERGY, List.of(3072L), COUNTER, List.of(3068L)
        ), GROUND_TRUTH);

        // when & then
        assertThat(tally.unionRecallAt(10)).isZero();
    }

    @Test
    @DisplayName("기록된 query가 없으면 0을 돌려준다")
    void recallAt_WhenNothingRecorded_IsZero() {
        assertThat(new RecallTally().recallAt(BUILD, 10)).isZero();
        assertThat(new RecallTally().unionRecallAt(10)).isZero();
    }

    @Test
    @DisplayName("구매 단계별로 recall을 따로 낼 수 있다 — 0코어와 4코어는 완전히 다른 문제다")
    void recallAtStep_WhenStepsDiffer_ReportsSeparately() {
        // given: 0코어에선 Build가 맞혔고, 3코어에선 놓쳤다
        RecallTally tally = new RecallTally();
        tally.record(0, Map.of(BUILD, List.of(GROUND_TRUTH)), GROUND_TRUTH);
        tally.record(3, Map.of(BUILD, List.of(6673L)), GROUND_TRUTH);

        // when & then
        assertThat(tally.recallAtStep(BUILD, 10, 0)).isEqualTo(1.0);
        assertThat(tally.recallAtStep(BUILD, 10, 3)).isZero();
    }

    @Test
    @DisplayName("단계별 union recall도 따로 낸다")
    void unionRecallAtStep_WhenStepsDiffer_ReportsSeparately() {
        // given
        RecallTally tally = new RecallTally();
        tally.record(0, Map.of(BUILD, List.of(GROUND_TRUTH), COUNTER, List.of(6673L)), GROUND_TRUTH);
        tally.record(4, Map.of(BUILD, List.of(6673L), COUNTER, List.of(GROUND_TRUTH)), GROUND_TRUTH);

        // when & then
        assertThat(tally.unionRecallAtStep(10, 0)).isEqualTo(1.0);
        assertThat(tally.unionRecallAtStep(10, 4)).isEqualTo(1.0);
        assertThat(tally.recallAtStep(BUILD, 10, 4)).isZero();
    }

    @Test
    @DisplayName("단계별 고유 기여를 낸다 — 깊은 코어에서 어느 generator가 실제로 일하는지 본다")
    void uniqueContributionAtStep_WhenOnlyOneGeneratorFinds_CountsIt() {
        // given: 4코어에서 Build는 놓치고 Ally만 찾았다
        RecallTally tally = new RecallTally();
        tally.record(4, Map.of(BUILD, List.of(6673L), ALLY_SYNERGY, List.of(GROUND_TRUTH)), GROUND_TRUTH);

        // when & then
        assertThat(tally.uniqueContributionAtStep(ALLY_SYNERGY, 10, 4)).isEqualTo(1.0);
        assertThat(tally.uniqueContributionAtStep(BUILD, 10, 4)).isZero();
    }

    @Test
    @DisplayName("단계별 표본 수를 센다 — 깊은 코어는 표본이 적어 수치 신뢰도가 다르다")
    void queryCountAtStep_WhenRecorded_CountsPerStep() {
        // given
        RecallTally tally = new RecallTally();
        tally.record(0, Map.of(BUILD, List.of(GROUND_TRUTH)), GROUND_TRUTH);
        tally.record(0, Map.of(BUILD, List.of(GROUND_TRUTH)), GROUND_TRUTH);
        tally.record(4, Map.of(BUILD, List.of(GROUND_TRUTH)), GROUND_TRUTH);

        // when & then
        assertThat(tally.queryCountAtStep(0)).isEqualTo(2);
        assertThat(tally.queryCountAtStep(4)).isEqualTo(1);
        assertThat(tally.observedSteps()).containsExactly(0, 4);
    }

    @Test
    @DisplayName("포지션별로 recall과 고유 기여를 따로 낸다 — Ally-Synergy의 존재 전제가 서포터다")
    void recallAtPosition_WhenPositionsDiffer_ReportsSeparately() {
        // given: 서포터에서는 Ally만 찾았고, 미드에서는 Build만 찾았다
        RecallTally tally = new RecallTally();
        tally.record(dfgg.domain.champion.ChampionPosition.SUPPORT, 1,
                Map.of(BUILD, List.of(6673L), ALLY_SYNERGY, List.of(GROUND_TRUTH)), GROUND_TRUTH);
        tally.record(dfgg.domain.champion.ChampionPosition.MID, 1,
                Map.of(BUILD, List.of(GROUND_TRUTH), ALLY_SYNERGY, List.of(6673L)), GROUND_TRUTH);

        // when & then
        assertThat(tally.recallAtPosition(ALLY_SYNERGY, 10, dfgg.domain.champion.ChampionPosition.SUPPORT))
                .isEqualTo(1.0);
        assertThat(tally.recallAtPosition(ALLY_SYNERGY, 10, dfgg.domain.champion.ChampionPosition.MID))
                .isZero();
        assertThat(tally.uniqueContributionAtPosition(
                ALLY_SYNERGY, 10, dfgg.domain.champion.ChampionPosition.SUPPORT)).isEqualTo(1.0);
        assertThat(tally.queryCountAtPosition(dfgg.domain.champion.ChampionPosition.SUPPORT)).isEqualTo(1);
    }

    @Test
    @DisplayName("기록한 query 수를 센다")
    void queryCount_WhenRecorded_CountsQueries() {
        // given
        RecallTally tally = new RecallTally();
        tally.record(Map.of(BUILD, List.of(GROUND_TRUTH)), GROUND_TRUTH);
        tally.record(Map.of(BUILD, List.of(GROUND_TRUTH)), GROUND_TRUTH);

        // when & then
        assertThat(tally.queryCount()).isEqualTo(2);
    }
}
