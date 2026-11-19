package dfgg.application.aggregation;

import java.util.List;

public record StatsAggregationReport(List<StepReport> steps, long durationMillis) {

    public record StepReport(String name, long durationMillis, Object result) {
    }
}
