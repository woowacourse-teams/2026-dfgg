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
    @DisplayName("lift가 문턱을 넘는 적을 지목한다")
    void championIdsFor_WhenCounterDroveTheScore_NamesEnemies() {

        assertThat(CounterEvidence.championIdsFor(candidateWithLifts(Map.of(RAMMUS, 1.7))))
                .containsExactly(RAMMUS);
    }

    @Test
    @DisplayName("두 명까지만 지목한다 — 다섯을 늘어놓으면 누구 때문인지 흐려진다")
    void championIdsFor_CapsAtTwoEnemies() {
        Map<Long, Double> threeEnemies = Map.of(RAMMUS, 1.9, AHRI, 2.4, JINX, 1.3);

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

    @Test
    @DisplayName("평소와 별 차이 없는 적은 빼둔다 — lift 1.09 같은 것은 근거가 아니다")
    void championIdsFor_WhenLiftIsBarelyAboveNeutral_DropsThatEnemy() {
        // topK를 5 → 10으로 넓히자 9~10위권의 약한 근거가 들어왔다. 실사례에서 애쉬의
        // 필멸자의 운명에 lift 1.09인 아군이 붙었는데, 3% 차이를 이유라 부를 수는 없다.
        Map<Long, Double> lifts = Map.of(RAMMUS, 1.09, AHRI, 1.15);

        assertThat(CounterEvidence.championIdsFor(candidateWithLifts(lifts))).isEmpty();
    }

    @Test
    @DisplayName("치유 감소가 필요한 조합은 살아남는다 — 문턱을 더 올리면 잃는 사례")
    void championIdsFor_WhenHealHeavyEnemies_KeepsThem() {
        // 실측: 애쉬 + 문도·마오카이·사일러스 → 필멸자의 운명.
        // 문도 1.36 / 사일러스 1.25는 남고, 회복이 약한 마오카이 0.87은 빠진다.
        Map<Long, Double> lifts = Map.of(RAMMUS, 1.36, AHRI, 1.25, JINX, 0.87);

        assertThat(CounterEvidence.championIdsFor(candidateWithLifts(lifts)))
                .containsExactly(RAMMUS, AHRI);
    }
}
