package dfgg.application.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.common.exception.StatsAggregationFailedException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatsAggregationRunnerTest {

    private final List<String> executed = new ArrayList<>();

    @Test
    @DisplayName("등록된 집계 단계를 순서 값대로 실행하고 단계별 결과를 보고한다")
    void runAll_WhenStepsRegistered_RunInOrder() {
        // given
        var runner = new StatsAggregationRunner(List.of(step("champion-positions", 20), step("item-stats", 10)));

        // when
        StatsAggregationReport report = runner.runAll();

        // then
        assertThat(executed).containsExactly("item-stats", "champion-positions");
        assertThat(report.steps()).extracting(StatsAggregationReport.StepReport::name)
                .containsExactly("item-stats", "champion-positions");
        assertThat(report.steps()).extracting(StatsAggregationReport.StepReport::result)
                .containsExactly("item-stats done", "champion-positions done");
    }

    @Test
    @DisplayName("한 단계가 실패하면 뒤 단계를 실행하지 않고 실패한 단계와 끝난 단계를 알린다")
    void runAll_WhenStepFails_StopAndNameFailedStep() {
        // given
        var runner = new StatsAggregationRunner(List.of(
                step("first", 10), failingStep("second", 20), step("third", 30)));

        // when & then
        assertThatThrownBy(runner::runAll)
                .isInstanceOf(StatsAggregationFailedException.class)
                .hasMessageContaining("second")
                .satisfies(exception -> {
                    var failure = (StatsAggregationFailedException) exception;
                    assertThat(failure.failedStep()).isEqualTo("second");
                    assertThat(failure.completedSteps()).containsExactly("first");
                })
                .hasRootCauseMessage("boom");
        assertThat(executed).containsExactly("first");
    }

    @Test
    @DisplayName("순서 값이 겹치는 단계는 등록할 수 없다")
    void construct_WhenOrderDuplicated_Reject() {
        // when & then
        assertThatThrownBy(() -> new StatsAggregationRunner(List.of(step("a", 10), step("b", 10))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10");
    }

    @Test
    @DisplayName("이름이 겹치는 단계는 등록할 수 없다")
    void construct_WhenNameDuplicated_Reject() {
        // when & then
        assertThatThrownBy(() -> new StatsAggregationRunner(List.of(step("a", 10), step("a", 20))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("a");
    }

    private StatsAggregationStep step(String name, int order) {
        return new StatsAggregationStep() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public Object aggregate() {
                executed.add(name);
                return name + " done";
            }
        };
    }

    private StatsAggregationStep failingStep(String name, int order) {
        return new StatsAggregationStep() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public Object aggregate() {
                throw new IllegalStateException("boom");
            }
        };
    }
}
