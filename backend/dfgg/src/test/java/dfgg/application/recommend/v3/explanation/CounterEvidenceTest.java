package dfgg.application.recommend.v3.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.SourceEvidence;
import dfgg.application.recommend.v3.feature.ReasonGroup;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 어떤 적을 이유로 댈지 정한다 — SHAP 게이트와 lift 문턱을 함께 본다.
 * <p>
 * 서비스에서 꺼내온 이유가 있다. 통합 테스트만으로는 두 규칙이 검증되지 않았다.
 * 픽스처에 적이 하나뿐이라 인원 제한이 걸릴 일이 없었고, 근거가 있는 아이템이 전부 게이트도 통과해서 게이트를 없애도 결과가 같았다.
 */
class CounterEvidenceTest {

    private static final long RAMMUS = 33L;
    private static final long AHRI = 103L;
    private static final long JINX = 222L;
    private static final long DOMINIK = 3036L;

    private static SelectedReasons gateOpen() {
        return new SelectedReasons(List.of(new GroupWeight(ReasonGroup.COUNTER, 1.2)));
    }

    private static SelectedReasons gateClosed() {
        return new SelectedReasons(List.of(new GroupWeight(ReasonGroup.BUILD, 2.4)));
    }

    private static ItemCandidate candidateWithLifts(Map<Long, Double> liftByEnemy) {
        return new ItemCandidate(DOMINIK, Map.of(
                CandidateSource.COUNTER, new SourceEvidence(1.7, 1, 0, liftByEnemy)));
    }

    @Test
    @DisplayName("counter가 점수를 올렸을 때만 적을 지목한다")
    void championIdsFor_WhenCounterDroveTheScore_NamesEnemies() {
        SelectedReasons selected = gateOpen();

        assertThat(CounterEvidence.championIdsFor(selected, candidateWithLifts(Map.of(RAMMUS, 1.7))))
                .containsExactly(RAMMUS);
    }

    @Test
    @DisplayName("counter가 점수를 올리지 않았으면 근거가 있어도 지목하지 않는다")
    void championIdsFor_WhenCounterDidNotDriveTheScore_NamesNobody() {
        // T15에서 counter 단독 후보가 28,468건인데 Top-5 진입은 1.7%였다. 게이트가 없으면
        // 모델이 무시한 적 이름이 대량으로 나간다.
        SelectedReasons selected = gateClosed();

        assertThat(CounterEvidence.championIdsFor(selected, candidateWithLifts(Map.of(RAMMUS, 1.7))))
                .isEmpty();
    }

    @Test
    @DisplayName("두 명까지만 지목한다 — 다섯을 늘어놓으면 누구 때문인지 흐려진다")
    void championIdsFor_CapsAtTwoEnemies() {
        Map<Long, Double> threeEnemies = Map.of(RAMMUS, 1.9, AHRI, 2.4, JINX, 1.2);

        assertThat(CounterEvidence.championIdsFor(gateOpen(), candidateWithLifts(threeEnemies)))
                .containsExactly(AHRI, RAMMUS);
    }

    @Test
    @DisplayName("lift가 1 이하인 적은 뺀다 — 평소와 같거나 덜 사는 아이템이다")
    void championIdsFor_DropsEnemiesAtOrBelowNeutralLift() {
        assertThat(CounterEvidence.championIdsFor(
                gateOpen(), candidateWithLifts(Map.of(RAMMUS, 0.39, AHRI, 1.61))))
                .containsExactly(AHRI);
    }

    @Test
    @DisplayName("counter가 찾지 않은 후보는 지목할 것이 없다")
    void championIdsFor_WhenCandidateHasNoCounterEvidence_IsEmpty() {
        ItemCandidate buildOnly = new ItemCandidate(DOMINIK, Map.of(
                CandidateSource.BUILD, new SourceEvidence(0.9, 1)));

        assertThat(CounterEvidence.championIdsFor(gateOpen(), buildOnly)).isEmpty();
    }

    @Test
    @DisplayName("base rate로 백오프한 후보는 아무도 지목하지 않는다")
    void championIdsFor_WhenBackedOffToBaseRate_IsEmpty() {
        // 백오프하면 적별 lift가 비어 있다. 적 덕이 아닌데 그렇게 말하면 거짓말이다.
        assertThat(CounterEvidence.championIdsFor(gateOpen(), candidateWithLifts(Map.of())))
                .isEmpty();
    }
}
