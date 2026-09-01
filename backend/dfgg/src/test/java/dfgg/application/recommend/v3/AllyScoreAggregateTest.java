package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dfgg.application.recommend.v3.generator.AllyScoreAggregate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AllyScoreAggregateTest {

    private static final long JINX = 222L;
    private static final long THRESH = 412L;
    private static final long LEE_SIN = 64L;
    private static final long ORNN = 516L;

    @Test
    @DisplayName("아군별 점수를 버리지 않고 그대로 보존한다 — 개별 점수가 LTR feature가 된다")
    void scoreOf_WhenAggregated_KeepsEachAllyScore() {
        // given
        AllyScoreAggregate aggregate = AllyScoreAggregate.of(Map.of(
                JINX, 0.8,
                THRESH, 0.3,
                LEE_SIN, 0.1
        ));

        // when & then
        assertThat(aggregate.scoreOf(JINX)).isEqualTo(0.8);
        assertThat(aggregate.scoreOf(THRESH)).isEqualTo(0.3);
        assertThat(aggregate.scoreOf(LEE_SIN)).isEqualTo(0.1);
    }

    @Test
    @DisplayName("관계가 관측되지 않은 아군의 점수는 0이다")
    void scoreOf_WhenAllyNotObserved_IsZero() {
        // given
        AllyScoreAggregate aggregate = AllyScoreAggregate.of(Map.of(JINX, 0.8));

        // when & then
        assertThat(aggregate.scoreOf(ORNN)).isZero();
    }

    @Test
    @DisplayName("max·mean·sum을 계산한다")
    void aggregate_WhenMultipleAllies_ComputesMaxMeanSum() {
        // given
        AllyScoreAggregate aggregate = AllyScoreAggregate.of(Map.of(
                JINX, 0.8,
                THRESH, 0.4,
                LEE_SIN, 0.3
        ));

        // when & then
        assertThat(aggregate.max()).isEqualTo(0.8);
        assertThat(aggregate.sum()).isCloseTo(1.5, within(1e-9));
        assertThat(aggregate.mean()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    @DisplayName("상위 1·2위 점수를 따로 뽑는다 — 특정 아군 하나와의 궁합이 결정적인 경우를 표현한다")
    void aggregate_WhenMultipleAllies_ComputesTop1AndTop2() {
        // given
        AllyScoreAggregate aggregate = AllyScoreAggregate.of(Map.of(
                JINX, 0.8,
                THRESH, 0.4,
                LEE_SIN, 0.3
        ));

        // when & then
        assertThat(aggregate.top1()).isEqualTo(0.8);
        assertThat(aggregate.top2()).isEqualTo(0.4);
    }

    @Test
    @DisplayName("아군이 하나뿐이면 2위 점수는 0이다")
    void top2_WhenOnlyOneAlly_IsZero() {
        // given
        AllyScoreAggregate aggregate = AllyScoreAggregate.of(Map.of(JINX, 0.8));

        // when & then
        assertThat(aggregate.top1()).isEqualTo(0.8);
        assertThat(aggregate.top2()).isZero();
    }

    @Test
    @DisplayName("아군 순서를 바꿔도 집계 결과가 동일하다 — 요청의 아군 나열 순서가 추천에 새면 안 된다")
    void aggregate_WhenAllyOrderChanges_ProducesIdenticalValues() {
        // given
        Map<Long, Double> forward = new LinkedHashMap<>();
        forward.put(JINX, 0.8);
        forward.put(THRESH, 0.4);
        forward.put(LEE_SIN, 0.3);

        Map<Long, Double> reversed = new LinkedHashMap<>();
        reversed.put(LEE_SIN, 0.3);
        reversed.put(THRESH, 0.4);
        reversed.put(JINX, 0.8);

        // when
        AllyScoreAggregate one = AllyScoreAggregate.of(forward);
        AllyScoreAggregate other = AllyScoreAggregate.of(reversed);

        // then
        assertThat(other).isEqualTo(one);
        assertThat(other.max()).isEqualTo(one.max());
        assertThat(other.mean()).isEqualTo(one.mean());
        assertThat(other.sum()).isEqualTo(one.sum());
        assertThat(other.top2()).isEqualTo(one.top2());
    }

    @Test
    @DisplayName("관측된 아군이 하나도 없으면 모든 집계가 0이다")
    void aggregate_WhenNoAllyObserved_IsAllZero() {
        // given
        AllyScoreAggregate aggregate = AllyScoreAggregate.of(Map.of());

        // when & then
        assertThat(aggregate.max()).isZero();
        assertThat(aggregate.mean()).isZero();
        assertThat(aggregate.sum()).isZero();
        assertThat(aggregate.top1()).isZero();
        assertThat(aggregate.top2()).isZero();
    }

    @Test
    @DisplayName("mean은 관측된 아군 수로 나눈다 — 관측 안 된 아군을 0으로 채워 평균을 낮추지 않는다")
    void mean_WhenSomeAlliesUnobserved_DividesByObservedCountOnly() {
        // given: 아군 4명 중 2명만 관측됐다
        AllyScoreAggregate aggregate = AllyScoreAggregate.of(Map.of(JINX, 0.8, THRESH, 0.4));

        // when & then
        assertThat(aggregate.mean()).isCloseTo(0.6, within(1e-9));
    }
}
