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

    /**
     * 승률 lift다 — {@code P(win | 나, 아군, item) / P(win | 나, item)}.
     * 1.0이 "이 아군과 함께여도 평소만큼 이긴다"이고, 넘어야 시너지라 부를 수 있다.
     * 구매 lift(랭킹에 쓰는 값)는 별도로 들고 있으므로 여기서는 승률만 준다.
     */
    private static ItemCandidate candidateWithAllyLifts(Map<Long, Double> winLiftByAlly) {
        return new ItemCandidate(ARDENT_CENSER, Map.of(
                CandidateSource.ALLY_SYNERGY,
                new SourceEvidence(2.4, 1, 0, Map.of(), winLiftByAlly)));
    }

    @Test
    @DisplayName("승률 lift가 문턱을 넘는 아군을 지목한다")
    void championIdsFor_WhenAllySynergyDroveTheScore_NamesAllies() {
        assertThat(AllyEvidence.championIdsFor(candidateWithAllyLifts(Map.of(JINX, 1.15))))
                .containsExactly(JINX);
    }

    @Test
    @DisplayName("문턱 이하인 아군은 뺀다 — 평소만큼만 이기면 이유가 아니다")
    void championIdsFor_WhenWinLiftIsNotAboveNeutral_DropsThatAlly() {
        // 승률 lift는 구매 lift보다 스케일이 작다. 1.01은 승률이 1% 높다는 뜻이라 노이즈다.
        Map<Long, Double> winLifts = Map.of(JINX, 1.15, KOGMAW, 1.02);

        assertThat(AllyEvidence.championIdsFor(candidateWithAllyLifts(winLifts)))
                .containsExactly(JINX);
    }

    @Test
    @DisplayName("최상위와 차이가 커도 문턱을 넘으면 함께 지목한다 — 상대 기준이 아니다")
    void championIdsFor_WhenFarBelowTopButStillAboveFloor_KeepsIt() {
        // 옛 상대 문턱(최상위의 절반)이었다면 1.05는 1.30의 절반에 못 미쳐 빠졌다.
        Map<Long, Double> winLifts = Map.of(JINX, 1.30, KOGMAW, 1.09);

        assertThat(AllyEvidence.championIdsFor(candidateWithAllyLifts(winLifts)))
                .containsExactly(JINX, KOGMAW);
    }

    @Test
    @DisplayName("모두가 평소 승률 근처면 아무도 지목하지 않는다 — 옛 상대 문턱의 결함")
    void championIdsFor_WhenEveryWinLiftIsNeutral_NamesNobody() {
        // 상대 문턱이었다면 최상위의 절반을 넘는 아군이 전부 통과했다.
        Map<Long, Double> winLifts = Map.of(JINX, 1.05, KOGMAW, 0.98, ORNN, 0.95);

        assertThat(AllyEvidence.championIdsFor(candidateWithAllyLifts(winLifts))).isEmpty();
    }

    @Test
    @DisplayName("아군이 하나뿐이어도 문턱을 넘으면 지목한다")
    void championIdsFor_WhenOnlyOneAllyAboveNeutral_NamesThatAlly() {
        assertThat(AllyEvidence.championIdsFor(candidateWithAllyLifts(Map.of(KOGMAW, 1.6))))
                .containsExactly(KOGMAW);
    }

    @Test
    @DisplayName("두 명까지만 지목한다")
    void championIdsFor_CapsAtTwoAllies() {
        Map<Long, Double> winLifts = Map.of(JINX, 1.25, KOGMAW, 1.20, ORNN, 1.15);

        assertThat(AllyEvidence.championIdsFor(candidateWithAllyLifts(winLifts)))
                .containsExactly(JINX, KOGMAW);
    }

    @Test
    @DisplayName("아군 시너지가 찾지 않은 후보는 지목할 것이 없다")
    void championIdsFor_WhenCandidateHasNoAllyEvidence_IsEmpty() {
        ItemCandidate buildOnly = new ItemCandidate(ARDENT_CENSER, Map.of(
                CandidateSource.BUILD, new SourceEvidence(0.9, 1)));

        assertThat(AllyEvidence.championIdsFor(buildOnly)).isEmpty();
    }

    @Test
    @DisplayName("base rate로 백오프한 후보는 아무도 지목하지 않는다")
    void championIdsFor_WhenBackedOffToBaseRate_IsEmpty() {
        assertThat(AllyEvidence.championIdsFor(candidateWithAllyLifts(Map.of())))
                .isEmpty();
    }

    @Test
    @DisplayName("lift가 모두 0이면 지목하지 않는다 — 관측되지 않았다는 뜻이다")
    void championIdsFor_WhenEveryLiftIsZero_IsEmpty() {
        assertThat(AllyEvidence.championIdsFor(candidateWithAllyLifts(Map.of(JINX, 0.0, KOGMAW, 0.0))))
                .isEmpty();
    }

    @Test
    @DisplayName("승률이 평소와 별 차이 없으면 빼둔다")
    void championIdsFor_WhenWinLiftIsBarelyAboveNeutral_DropsThatAlly() {
        Map<Long, Double> lifts = Map.of(JINX, 1.06, KOGMAW, 1.04);

        assertThat(AllyEvidence.championIdsFor(candidateWithAllyLifts(lifts))).isEmpty();
    }

    @Test
    @DisplayName("구매 lift가 높아도 승률 lift가 낮으면 지목하지 않는다 — 드래프트 상관을 걸러낸다")
    void championIdsFor_WhenPurchaseLiftHighButWinLiftLow_NamesNobody() {
        // 실사례: 애쉬+룰루의 도미닉은 구매 lift 1.21이지만, 룰루 때문에 이기는 것이 아니다.
        // 적이 탱커일 때 도미닉을 사는 것이고, 그 상관이 아군 쪽으로 새어 든 것이다.
        ItemCandidate purchaseHeavy = new ItemCandidate(ARDENT_CENSER, Map.of(
                CandidateSource.ALLY_SYNERGY,
                new SourceEvidence(2.4, 1, 0, Map.of(JINX, 3.5), Map.of(JINX, 1.02))));

        assertThat(AllyEvidence.championIdsFor(purchaseHeavy)).isEmpty();
    }

    @Test
    @DisplayName("승률 표본이 얇아 lift를 못 구한 아군은 지목하지 않는다")
    void championIdsFor_WhenWinLiftMissing_NamesNobody() {
        ItemCandidate noWinLift = new ItemCandidate(ARDENT_CENSER, Map.of(
                CandidateSource.ALLY_SYNERGY,
                new SourceEvidence(2.4, 1, 0, Map.of(JINX, 3.5), Map.of())));

        assertThat(AllyEvidence.championIdsFor(noWinLift)).isEmpty();
    }
}
