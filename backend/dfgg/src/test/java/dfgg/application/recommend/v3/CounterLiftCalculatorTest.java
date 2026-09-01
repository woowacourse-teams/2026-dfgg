package dfgg.application.recommend.v3;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.application.recommend.v3.generator.CounterLift;
import dfgg.application.recommend.v3.generator.CounterLiftCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CounterLiftCalculatorTest {

    private static final double ALPHA = 1.0;
    private static final int VOCABULARY_SIZE = 159;

    private final CounterLiftCalculator calculator = new CounterLiftCalculator(ALPHA, VOCABULARY_SIZE);

    @Test
    @DisplayName("이 적을 만났을 때 평소보다 많이 사면 lift가 1보다 크다")
    void calculate_WhenBoughtMoreAgainstThisEnemy_LiftExceedsOne() {
        // given: 평소 100판 중 10판(10%)인데, 이 적을 만난 50판 중 30판(60%)이다
        CounterLift lift = calculator.calculate(30, 50, 10, 100);

        // then
        assertThat(lift.lift()).isGreaterThan(1.0);
    }

    @Test
    @DisplayName("이 적을 만났을 때 평소보다 적게 사면 lift가 1보다 작다")
    void calculate_WhenBoughtLessAgainstThisEnemy_LiftIsBelowOne() {
        // given: 평소 60%인데 이 적 상대로는 10%
        CounterLift lift = calculator.calculate(5, 50, 60, 100);

        // then
        assertThat(lift.lift()).isLessThan(1.0);
    }

    @Test
    @DisplayName("평소 구매율과 같으면 lift가 1 근처다 — 적 때문에 값이 오른 게 아니다")
    void calculate_WhenSameAsBaseRate_LiftIsAroundOne() {
        // given: 둘 다 30%
        CounterLift lift = calculator.calculate(15, 50, 30, 100);

        // then
        assertThat(lift.lift()).isBetween(0.8, 1.25);
    }

    @Test
    @DisplayName("한 번도 안 산 아이템이어도 lift가 발산하지 않는다 — 0으로 나누지 않는다")
    void calculate_WhenBaseRateIsZero_LiftStaysFinite() {
        // given: 이 챔피언은 1000판 동안 이 아이템을 산 적이 없는데, 이 적 상대로만 관측됐다
        CounterLift lift = calculator.calculate(10, 10, 0, 1000);

        // then
        assertThat(lift.lift()).isFinite();
        assertThat(lift.lift()).isNotNaN();
    }

    @Test
    @DisplayName("표본이 적으면 lift가 1쪽으로 수축한다 — 두세 판의 우연을 강한 신호로 읽지 않는다")
    void calculate_WhenPairSampleIsSmall_ShrinksTowardOne() {
        // given: 원 비율로는 100%/1% = 100배지만 표본이 5판뿐이다
        CounterLift smallSample = calculator.calculate(5, 5, 1, 100);
        // 같은 비율이지만 표본이 20배 큰 경우
        CounterLift largeSample = calculator.calculate(100, 100, 20, 2000);

        // then: 표본이 클수록 원 비율에 가까워진다
        assertThat(smallSample.lift()).isLessThan(largeSample.lift());
        assertThat(smallSample.lift()).isLessThan(100.0);
    }

    @Test
    @DisplayName("lift와 원 확률과 base rate를 각각 따로 보존한다 — LTR이 셋을 구분해서 본다")
    void calculate_WhenComputed_KeepsPairProbabilityAndBaseRateSeparately() {
        // given
        CounterLift lift = calculator.calculate(30, 50, 10, 100);

        // then: 스무딩 전 원 확률이 그대로 남아야 "base rate가 바닥인데 lift만 높다"를 판별할 수 있다
        assertThat(lift.pairProbability()).isEqualTo(30.0 / 50.0);
        assertThat(lift.baseRate()).isEqualTo(10.0 / 100.0);
    }

    @Test
    @DisplayName("base rate가 바닥이면 lift가 높아도 base rate 자체는 바닥으로 남는다")
    void calculate_WhenBaseRateIsFloor_ReportsFloorBaseRateAlongsideHighLift() {
        // given: AD 챔피언이 거의 안 사는 아이템이 특정 적 상대로만 관측된 상황
        CounterLift lift = calculator.calculate(8, 10, 1, 1000);

        // then: lift는 크지만 base rate는 0.001이라는 사실이 그대로 남는다
        assertThat(lift.lift()).isGreaterThan(1.0);
        assertThat(lift.baseRate()).isEqualTo(0.001);
    }

    @Test
    @DisplayName("함께한 판이 0이면 lift는 1이다 — 근거가 없다는 뜻이지 나쁘다는 뜻이 아니다")
    void calculate_WhenNoPairGames_LiftIsNeutral() {
        // given
        CounterLift lift = calculator.calculate(0, 0, 10, 100);

        // then
        assertThat(lift.lift()).isEqualTo(1.0);
    }
}
