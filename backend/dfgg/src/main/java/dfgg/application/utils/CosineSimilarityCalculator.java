package dfgg.application.utils;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CosineSimilarityCalculator {

    public double compute(List<Double> a, List<Double> b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 여러 벡터를 평균으로 뭉치지 않고 각각과 개별 비교한 뒤 최댓값을 취한다
     * (ColBERT의 late-interaction/MaxSim과 동일한 아이디어).
     */
    public double maxSimilarity(List<Double> vector, List<List<Double>> others) {
        return others.stream()
                .mapToDouble(other -> compute(vector, other))
                .max()
                .getAsDouble();
    }
}
