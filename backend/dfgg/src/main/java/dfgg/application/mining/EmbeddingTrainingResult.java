package dfgg.application.mining;

public record EmbeddingTrainingResult(
        long persistedEmbeddingCount,
        String algorithmVersion,
        int matchCount,
        int windowCount,
        long trainingDurationMillis
) {

}
