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
 * 어떤 아군을 추천 이유로 댈지 정한다. counter와 같은 기준을 쓴다 — lift {@code > 1}.
 * <p>
 * 한때는 "최상위 아군 점수의 절반"이라는 상대 문턱이었다. 아군 점수가
 * {@code P(item | 나, 아군)}의 Wilson 하한, 즉 확률이라 1을 넘을 수 없었기 때문이다.
 * 그런데 상대 기준은 <b>아군 넷의 점수가 고만고만하면 아무나 통과</b>시킨다.
 * 실측에서 원딜에게 무한의 대검이 추천될 때 이렐리아·요네·피즈가 이유로 붙었다 —
 * 원딜이면 거의 다 사는 아이템이라 특정 아군으로 설명될 이유가 없는데도 그랬다.
 * <p>
 * 점수를 lift {@code P(item | 나, 아군) / P(item | 나)}로 바꾸면 "평소보다 더 산다"가 되어
 * 그런 아이템이 자연히 걸러진다. counter가 이미 그렇게 동작한다.
 */
class AllyEvidenceTest {

    private static final long JINX = 222L;
    private static final long KOGMAW = 96L;
    private static final long ORNN = 516L;
    private static final long ARDENT_CENSER = 3504L;

    private static SelectedReasons gateOpen() {
        return new SelectedReasons(List.of(new GroupWeight(ReasonGroup.ALLY_SYNERGY, 0.9)));
    }

    private static SelectedReasons gateClosed() {
        return new SelectedReasons(List.of(new GroupWeight(ReasonGroup.BUILD, 2.4)));
    }

    /** 점수는 이제 lift다 — 1.0이 "평소와 같다"이고 그 아래는 오히려 덜 산다는 뜻이다. */
    private static ItemCandidate candidateWithAllyLifts(Map<Long, Double> liftByAlly) {
        return new ItemCandidate(ARDENT_CENSER, Map.of(
                CandidateSource.ALLY_SYNERGY, new SourceEvidence(2.4, 1, 0, liftByAlly)));
    }

    @Test
    @DisplayName("아군 시너지가 점수를 올렸을 때만 아군을 지목한다")
    void championIdsFor_WhenAllySynergyDroveTheScore_NamesAllies() {
        assertThat(AllyEvidence.championIdsFor(
                gateOpen(), candidateWithAllyLifts(Map.of(JINX, 2.4))))
                .containsExactly(JINX);
    }

    @Test
    @DisplayName("아군 시너지가 점수를 올리지 않았으면 근거가 있어도 지목하지 않는다")
    void championIdsFor_WhenAllySynergyDidNotDriveTheScore_NamesNobody() {
        assertThat(AllyEvidence.championIdsFor(
                gateClosed(), candidateWithAllyLifts(Map.of(JINX, 2.4))))
                .isEmpty();
    }

    @Test
    @DisplayName("lift가 1 이하인 아군은 뺀다 — 평소보다 더 사는 게 아니면 이유가 아니다")
    void championIdsFor_WhenLiftIsNotAboveNeutral_DropsThatAlly() {
        Map<Long, Double> lifts = Map.of(JINX, 2.4, KOGMAW, 1.0);

        assertThat(AllyEvidence.championIdsFor(gateOpen(), candidateWithAllyLifts(lifts)))
                .containsExactly(JINX);
    }

    @Test
    @DisplayName("최상위와 차이가 커도 lift가 1을 넘으면 함께 지목한다 — 상대 기준이 아니다")
    void championIdsFor_WhenFarBelowTopButStillAboveNeutral_KeepsIt() {
        // 옛 상대 문턱(최상위의 절반)이었다면 1.1은 5.0의 절반에 못 미쳐 빠졌다.
        Map<Long, Double> lifts = Map.of(JINX, 5.0, KOGMAW, 1.1);

        assertThat(AllyEvidence.championIdsFor(gateOpen(), candidateWithAllyLifts(lifts)))
                .containsExactly(JINX, KOGMAW);
    }

    @Test
    @DisplayName("모두가 고만고만해도 lift가 1 이하면 아무도 지목하지 않는다 — 옛 상대 문턱의 결함")
    void championIdsFor_WhenEveryLiftIsNeutral_NamesNobody() {
        // 무한의 대검처럼 원딜이면 다 사는 아이템이 이렇게 나온다.
        // 상대 문턱이었다면 최상위의 절반을 넘는 아군이 전부 통과했다.
        Map<Long, Double> lifts = Map.of(JINX, 1.0, KOGMAW, 0.98, ORNN, 0.95);

        assertThat(AllyEvidence.championIdsFor(gateOpen(), candidateWithAllyLifts(lifts)))
                .isEmpty();
    }

    @Test
    @DisplayName("아군이 하나뿐이어도 lift가 1을 넘으면 지목한다")
    void championIdsFor_WhenOnlyOneAllyAboveNeutral_NamesThatAlly() {
        assertThat(AllyEvidence.championIdsFor(
                gateOpen(), candidateWithAllyLifts(Map.of(KOGMAW, 1.6))))
                .containsExactly(KOGMAW);
    }

    @Test
    @DisplayName("두 명까지만 지목한다")
    void championIdsFor_CapsAtTwoAllies() {
        Map<Long, Double> lifts = Map.of(JINX, 3.0, KOGMAW, 2.5, ORNN, 2.0);

        assertThat(AllyEvidence.championIdsFor(gateOpen(), candidateWithAllyLifts(lifts)))
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
        assertThat(AllyEvidence.championIdsFor(gateOpen(), candidateWithAllyLifts(Map.of())))
                .isEmpty();
    }

    @Test
    @DisplayName("lift가 모두 0이면 지목하지 않는다 — 관측되지 않았다는 뜻이다")
    void championIdsFor_WhenEveryLiftIsZero_IsEmpty() {
        assertThat(AllyEvidence.championIdsFor(
                gateOpen(), candidateWithAllyLifts(Map.of(JINX, 0.0, KOGMAW, 0.0))))
                .isEmpty();
    }
}
