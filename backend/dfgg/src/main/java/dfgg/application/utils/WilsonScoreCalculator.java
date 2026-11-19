package dfgg.application.utils;

import org.springframework.stereotype.Component;

@Component
public class WilsonScoreCalculator {

    private static final double Z_95 = 1.96;

    public double lowerBound(int successes, int total) {
        if (total == 0) {
            return 0.0;
        }
        double p = (double) successes / total;
        double z2 = Z_95 * Z_95;
        double denominator = 1 + z2 / total;
        double centre = p + z2 / (2 * total);
        double margin = Z_95 * Math.sqrt((p * (1 - p) + z2 / (4 * total)) / total);
        return (centre - margin) / denominator;
    }
}
