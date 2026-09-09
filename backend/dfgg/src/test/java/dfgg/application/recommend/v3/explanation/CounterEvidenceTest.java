package dfgg.application.recommend.v3.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.ItemCandidate;
import dfgg.application.recommend.v3.SourceEvidence;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 어떤 적을 이유로 댈지 정한다 — 소속과 lift 문턱만 본다.
 * <p>
 * 한때 SHAP 지분 게이트가 하나 더 있었다. 걷어낸 이유는 SHAP이 "예측을 얼마나 밀었나"를
 * 답할 뿐 "이유인가"를 답하지 못하기 때문이다. 실제로 필멸자의 운명이 치유 감소가 필요한
 * 적 조합 때문에 1위로 올라왔는데(COUNTER 기여 −0.153 → +0.170) 지분이 8.68%라 설명하지
 * 못했다. 지분은 다른 묶음이 세지면 눌리므로 근거의 질과 무관하게 오르내린다.
 */
class CounterEvidenceTest {

    private static final long RAMMUS = 33L;
    private static final long AHRI = 103L;
    private static final long JINX = 222L;
    private static final long DOMINIK = 3036L;

    private static ItemCandidate candidateWithLifts(Map<Long, Double> liftByEnemy) {
        return new ItemCandidate(DOMINIK, Map.of(
                CandidateSource.COUNTER, new SourceEvidence(1.7, 1, 0, liftByEnemy)));
    }

    @Test
    @DisplayName("lift가 1을 넘는 적을 지목한다")
    void championIdsFor_WhenCounterDroveTheScore_NamesEnemies() {

        assertThat(CounterEvidence.championIdsFor(candidateWithLifts(Map.of(RAMMUS, 1.7))))
                .containsExactly(RAMMUS);
    }

    @Test
    @DisplayName("두 명까지만 지목한다 — 다섯을 늘어놓으면 누구 때문인지 흐려진다")
    void championIdsFor_CapsAtTwoEnemies() {
        Map<Long, Double> threeEnemies = Map.of(RAMMUS, 1.9, AHRI, 2.4, JINX, 1.2);

        assertThat(CounterEvidence.championIdsFor(candidateWithLifts(threeEnemies)))
                .containsExactly(AHRI, RAMMUS);
    }

    @Test
    @DisplayName("lift가 1 이하인 적은 뺀다 — 평소와 같거나 덜 사는 아이템이다")
    void championIdsFor_DropsEnemiesAtOrBelowNeutralLift() {
        assertThat(CounterEvidence.championIdsFor(candidateWithLifts(Map.of(RAMMUS, 0.39, AHRI, 1.61))))
                .containsExactly(AHRI);
    }

    @Test
    @DisplayName("counter가 찾지 않은 후보는 지목할 것이 없다")
    void championIdsFor_WhenCandidateHasNoCounterEvidence_IsEmpty() {
        ItemCandidate buildOnly = new ItemCandidate(DOMINIK, Map.of(
                CandidateSource.BUILD, new SourceEvidence(0.9, 1)));

        assertThat(CounterEvidence.championIdsFor(buildOnly)).isEmpty();
    }

    @Test
    @DisplayName("base rate로 백오프한 후보는 아무도 지목하지 않는다")
    void championIdsFor_WhenBackedOffToBaseRate_IsEmpty() {
        // 백오프하면 적별 lift가 비어 있다. 적 덕이 아닌데 그렇게 말하면 거짓말이다.
        assertThat(CounterEvidence.championIdsFor(candidateWithLifts(Map.of())))
                .isEmpty();
    }
}
