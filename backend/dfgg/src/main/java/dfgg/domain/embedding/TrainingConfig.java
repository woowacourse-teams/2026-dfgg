package dfgg.domain.embedding;

public record TrainingConfig(
        int dimensions,
        int negativeSamples,
        int epochs,
        double learningRate,
        long randomSeed
) {

}
