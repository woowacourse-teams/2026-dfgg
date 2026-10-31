package dfgg.application.mining;

import java.util.HashMap;
import java.util.Map;

public class ItemFrequencyWeights {

    private static final double DEFAULT_WEIGHT = 1.0;

    private final Map<String, Double> weightByItemToken;

    private ItemFrequencyWeights(Map<String, Double> weightByItemToken) {
        this.weightByItemToken = weightByItemToken;
    }

    public static ItemFrequencyWeights from(Map<String, Long> occurrenceCountByItemToken, long totalParticipantCount) {
        Map<String, Double> weightByItemToken = new HashMap<>();
        for (Map.Entry<String, Long> entry : occurrenceCountByItemToken.entrySet()) {
            double weight = Math.log((double) totalParticipantCount / entry.getValue());
            weightByItemToken.put(entry.getKey(), Math.max(0.0, weight));
        }
        return new ItemFrequencyWeights(weightByItemToken);
    }

    public double weightFor(String itemToken) {
        return weightByItemToken.getOrDefault(itemToken, DEFAULT_WEIGHT);
    }
}
