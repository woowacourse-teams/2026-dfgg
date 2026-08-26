package dfgg.application.mining;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemFrequencyWeightsTest {

    @Test
    @DisplayName("전체 참가자 대비 드물게 등장한 아이템일수록 더 높은 가중치를 받는다")
    void weightFor_WhenItemIsRarer_ReturnsHigherWeightThanFrequentItem() {
        // given: 참가자 100명 중 아이템 A는 1명만, 아이템 B는 90명이 샀다
        ItemFrequencyWeights weights = ItemFrequencyWeights.from(Map.of("A", 1L, "B", 90L), 100L);

        // when
        double rareItemWeight = weights.weightFor("A");
        double frequentItemWeight = weights.weightFor("B");

        // then
        assertThat(rareItemWeight).isGreaterThan(frequentItemWeight);
    }

    @Test
    @DisplayName("전체 참가자가 다 산 아이템은 가중치가 0에 가깝다")
    void weightFor_WhenItemBoughtByEveryParticipant_ReturnsWeightCloseToZero() {
        // given: 참가자 100명 전원이 아이템 A를 샀다
        ItemFrequencyWeights weights = ItemFrequencyWeights.from(Map.of("A", 100L), 100L);

        // when
        double weight = weights.weightFor("A");

        // then
        assertThat(weight).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("빈도 정보가 없는 아이템은 기본 가중치 1.0을 반환한다")
    void weightFor_WhenItemHasNoFrequencyData_ReturnsDefaultWeightOfOne() {
        // given
        ItemFrequencyWeights weights = ItemFrequencyWeights.from(Map.of("A", 1L), 100L);

        // when
        double weight = weights.weightFor("UNKNOWN_ITEM");

        // then
        assertThat(weight).isEqualTo(1.0);
    }

    @Test
    @DisplayName("가중치는 음수가 될 수 없다")
    void weightFor_NeverReturnsNegativeWeight() {
        // given: 방어적으로 극단적인 입력을 줘도 음수가 나오면 안 된다
        ItemFrequencyWeights weights = ItemFrequencyWeights.from(Map.of("A", 100L), 100L);

        // when
        double weight = weights.weightFor("A");

        // then
        assertThat(weight).isGreaterThanOrEqualTo(0.0);
    }
}
