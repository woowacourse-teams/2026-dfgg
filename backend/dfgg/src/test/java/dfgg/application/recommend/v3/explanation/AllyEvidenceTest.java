package dfgg.application.recommend.v3.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.SourceEvidence;
import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 어떤 아군을 추천 이유로 댈지 정한다.
 * <p>
 * counter와 관문 구조는 같지만 문턱의 성격이 다르다.
 * counter의 점수는 lift(평소 대비 배수)라 1을 넘는지 보면 되는데, 아군 점수는 {@code P(item | 나, 아군)}의 Wilson 하한이라 확률이다.
 * 1을 넘을 수 없으니 같은 기준을 쓸 수 없다.
 * <p>
 * 그래서 최상위 아군 점수의 절반을 문턱으로 둔다.
 * 실측에서 불타는 향로는 징크스 0.490, 코그모 0.018로 27배 차이가 났다 —
 * 랭킹에 쓴 점수는 최댓값이므로, 거기서 한참 떨어진 아군을 이유로 대면 잘못된 귀속이다.
 */
class AllyEvidenceTest {

    private static final long JINX = 222L;
    private static final long KOGMAW = 96L;
    private static final long ORNN = 516L;
    private static final long ARDENT_CENSER = 3504L;

    private static SelectedReasons gateOpen() {
        return new SelectedReasons(
                List.of(new GroupWeight(ReasonGroup.ALLY_SYNERGY, 0.9)), Optional.empty());
    }

    private static SelectedReasons gateClosed() {
        return new SelectedReasons(
                List.of(new GroupWeight(ReasonGroup.BUILD, 2.4)), Optional.empty());
    }

    private static ItemCandidate candidateWithAllyScores(Map<Long, Double> scoreByAlly) {
        return new ItemCandidate(ARDENT_CENSER, Map.of(
                CandidateSource.ALLY_SYNERGY, new SourceEvidence(0.49, 1, 0, scoreByAlly)));
    }

    @Test
    @DisplayName("아군 시너지가 점수를 올렸을 때만 아군을 지목한다")
    void championIdsFor_WhenAllySynergyDroveTheScore_NamesAllies() {
        assertThat(AllyEvidence.championIdsFor(
                gateOpen(), candidateWithAllyScores(Map.of(JINX, 0.49))))
                .containsExactly(JINX);
    }

    @Test
    @DisplayName("아군 시너지가 점수를 올리지 않았으면 근거가 있어도 지목하지 않는다")
    void championIdsFor_WhenAllySynergyDidNotDriveTheScore_NamesNobody() {
        assertThat(AllyEvidence.championIdsFor(
                gateClosed(), candidateWithAllyScores(Map.of(JINX, 0.49))))
                .isEmpty();
    }

    @Test
    @DisplayName("최상위의 절반에 못 미치는 아군은 뺀다 — 랭킹은 최댓값으로 매겨졌다")
    void championIdsFor_DropsAlliesFarBelowTheTopScore() {
        // 불타는 향로의 실측값. 코그모는 징크스의 27분의 1이다.
        Map<Long, Double> scores = Map.of(JINX, 0.490, KOGMAW, 0.018);

        assertThat(AllyEvidence.championIdsFor(gateOpen(), candidateWithAllyScores(scores)))
                .containsExactly(JINX);
    }

    @Test
    @DisplayName("최상위에 가까운 아군은 함께 지목한다 — 둘 다 이유가 맞다")
    void championIdsFor_KeepsAlliesCloseToTheTop() {
        Map<Long, Double> scores = Map.of(JINX, 0.50, KOGMAW, 0.45);

        assertThat(AllyEvidence.championIdsFor(gateOpen(), candidateWithAllyScores(scores)))
                .containsExactly(JINX, KOGMAW);
    }

    @Test
    @DisplayName("아군이 하나뿐이면 그 아군을 지목한다 — 상대 문턱이 자기 자신을 막지 않는다")
    void championIdsFor_WhenOnlyOneAlly_NamesThatAlly() {
        assertThat(AllyEvidence.championIdsFor(
                gateOpen(), candidateWithAllyScores(Map.of(KOGMAW, 0.596))))
                .containsExactly(KOGMAW);
    }

    @Test
    @DisplayName("두 명까지만 지목한다")
    void championIdsFor_CapsAtTwoAllies() {
        Map<Long, Double> scores = Map.of(JINX, 0.50, KOGMAW, 0.48, ORNN, 0.46);

        assertThat(AllyEvidence.championIdsFor(gateOpen(), candidateWithAllyScores(scores)))
                .containsExactly(JINX, KOGMAW);
    }

    @Test
    @DisplayName("아군 시너지가 찾지 않은 후보는 지목할 것이 없다")
    void championIdsFor_WhenCandidateHasNoAllyEvidence_IsEmpty() {
        ItemCandidate buildOnly = new ItemCandidate(ARDENT_CENSER, Map.of(
                CandidateSource.BUILD, new SourceEvidence(0.9, 1)));

        assertThat(AllyEvidence.championIdsFor(gateOpen(), buildOnly)).isEmpty();
    }

    @Test
    @DisplayName("base rate로 백오프한 후보는 아무도 지목하지 않는다")
    void championIdsFor_WhenBackedOffToBaseRate_IsEmpty() {
        assertThat(AllyEvidence.championIdsFor(gateOpen(), candidateWithAllyScores(Map.of())))
                .isEmpty();
    }

    @Test
    @DisplayName("점수가 모두 0이면 지목하지 않는다 — 관측되지 않았다는 뜻이다")
    void championIdsFor_WhenEveryScoreIsZero_IsEmpty() {
        assertThat(AllyEvidence.championIdsFor(
                gateOpen(), candidateWithAllyScores(Map.of(JINX, 0.0, KOGMAW, 0.0))))
                .isEmpty();
    }
}
