package dfgg.application.aggregation;

import dfgg.common.exception.StatsAggregationFailedException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 등록된 집계 단계를 순서대로 실행한다. 한 단계가 실패하면 뒤 단계는 실행하지 않는다. */
@Service
public class StatsAggregationRunner {
    private static final Logger log = LoggerFactory.getLogger(StatsAggregationRunner.class);

    private final List<StatsAggregationStep> steps;

    public StatsAggregationRunner(List<StatsAggregationStep> steps) {
        requireUnique(steps);
        this.steps = steps.stream().sorted(Comparator.comparingInt(StatsAggregationStep::order)).toList();
    }

    public StatsAggregationReport runAll() {
        long started = System.nanoTime();
        List<StatsAggregationReport.StepReport> reports = new ArrayList<>();
        for (StatsAggregationStep step : steps) {
            reports.add(run(step, reports));
        }
        return new StatsAggregationReport(List.copyOf(reports), elapsedMillis(started));
    }

    private StatsAggregationReport.StepReport run(StatsAggregationStep step,
                                                  List<StatsAggregationReport.StepReport> completed) {
        long started = System.nanoTime();
        try {
            Object result = step.aggregate();
            long duration = elapsedMillis(started);
            log.info("집계 단계 완료: step={}, durationMillis={}", step.name(), duration);
            return new StatsAggregationReport.StepReport(step.name(), duration, result);
        } catch (RuntimeException exception) {
            List<String> completedSteps = completed.stream().map(StatsAggregationReport.StepReport::name).toList();
            throw new StatsAggregationFailedException(step.name(), completedSteps, exception);
        }
    }

    private static void requireUnique(List<StatsAggregationStep> steps) {
        Set<String> names = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (StatsAggregationStep step : steps) {
            if (!names.add(step.name())) {
                throw new IllegalStateException("집계 단계 이름이 겹친다: " + step.name());
            }
            if (!orders.add(step.order())) {
                throw new IllegalStateException("집계 단계 순서가 겹친다: " + step.order());
            }
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
