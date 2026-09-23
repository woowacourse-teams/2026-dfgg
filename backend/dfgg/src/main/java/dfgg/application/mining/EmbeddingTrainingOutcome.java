package dfgg.application.mining;

import java.util.Map;

public record EmbeddingTrainingOutcome(
        Map<String, double[]> embeddings,
        int matchCount,
        int windowCount,
        long trainingDurationMillis
) {

}
