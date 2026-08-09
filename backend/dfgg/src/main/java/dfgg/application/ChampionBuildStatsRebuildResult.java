package dfgg.application;

public record ChampionBuildStatsRebuildResult(
        int totalMatches,
        int processedMatches,
        int skippedMissingTimeline,
        int recordedSamples
) {
}
