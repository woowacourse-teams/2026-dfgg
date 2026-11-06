package dfgg.common.exception;

import java.util.List;

/** 전체 집계 중 한 단계가 실패했다. 앞 단계의 결과는 이미 커밋돼 있다. */
public class StatsAggregationFailedException extends RuntimeException {
    private final String failedStep;
    private final List<String> completedSteps;

    public StatsAggregationFailedException(String failedStep, List<String> completedSteps, Throwable cause) {
        super("집계 단계 실패: " + failedStep, cause);
        this.failedStep = failedStep;
        this.completedSteps = List.copyOf(completedSteps);
    }

    public String failedStep() {
        return failedStep;
    }

    public List<String> completedSteps() {
        return completedSteps;
    }
}
