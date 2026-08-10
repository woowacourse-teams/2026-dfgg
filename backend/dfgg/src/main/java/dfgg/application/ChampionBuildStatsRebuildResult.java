package dfgg.application;

import java.util.List;
import java.util.Objects;

public record ChampionBuildStatsRebuildResult(
        int totalMatches,
        int processedMatches,
        int skippedMissingTimeline,
        int failedMatches,
        int recordedSamples,
        List<Failure> failures
) {

    public ChampionBuildStatsRebuildResult {
        failures = List.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
        if (failedMatches != failures.size()) {
            throw new IllegalArgumentException("failedMatches must equal failures size");
        }
    }

    public ChampionBuildStatsRebuildResult(
            int totalMatches,
            int processedMatches,
            int skippedMissingTimeline,
            int recordedSamples
    ) {
        this(totalMatches, processedMatches, skippedMissingTimeline, 0, recordedSamples, List.of());
    }

    public record Failure(String matchId, String reason) {

        public Failure {
            Objects.requireNonNull(matchId, "matchId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
