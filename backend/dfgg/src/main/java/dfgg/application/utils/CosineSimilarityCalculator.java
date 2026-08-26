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
}
