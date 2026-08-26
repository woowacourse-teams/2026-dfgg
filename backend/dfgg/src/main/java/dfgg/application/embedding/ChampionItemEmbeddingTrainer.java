package dfgg.application.embedding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import dfgg.domain.embedding.Window;
import dfgg.domain.embedding.TrainingConfig;
import org.springframework.stereotype.Component;

@Component
public class ChampionItemEmbeddingTrainer {

    private static final double INIT_RANGE = 0.5;

    public Map<String, double[]> train(List<Window> windows, TrainingConfig config) {
        List<String> vocabulary = extractVocabulary(windows);
        Random random = new Random(config.randomSeed());

        Map<String, double[]> inputVectors = initializeVectors(vocabulary, config.dimensions(), random);
        Map<String, double[]> outputVectors = initializeVectors(vocabulary, config.dimensions(), random);

        for (int epoch = 0; epoch < config.epochs(); epoch++) {
            for (Window window : windows) {
                trainWindow(window, vocabulary, inputVectors, outputVectors, config, random);
            }
        }
        return inputVectors;
    }

    private List<String> extractVocabulary(List<Window> windows) {
        Set<String> vocabulary = new LinkedHashSet<>();
        for (Window window : windows) {
            vocabulary.addAll(window.tokens());
        }
        return new ArrayList<>(vocabulary);
    }

    private Map<String, double[]> initializeVectors(List<String> vocabulary, int dimensions, Random random) {
        Map<String, double[]> vectors = new HashMap<>();
        for (String token : vocabulary) {
            double[] vector = new double[dimensions];
            for (int i = 0; i < dimensions; i++) {
                vector[i] = (random.nextDouble() - 0.5) * INIT_RANGE / dimensions;
            }
            vectors.put(token, vector);
        }
        return vectors;
    }

    private void trainWindow(
            Window window,
            List<String> vocabulary,
            Map<String, double[]> inputVectors,
            Map<String, double[]> outputVectors,
            TrainingConfig config,
            Random random
    ) {
        List<String> tokens = window.tokens();
        double weightedLearningRate = config.learningRate() * window.weight();
        for (int i = 0; i < tokens.size(); i++) {
            String target = tokens.get(i);
            for (int j = 0; j < tokens.size(); j++) {
                if (i == j) {
                    continue;
                }
                String context = tokens.get(j);
                updatePair(inputVectors.get(target), outputVectors.get(context), 1.0, weightedLearningRate);

                Set<String> exclude = Set.of(target, context);
                for (int k = 0; k < config.negativeSamples(); k++) {
                    String negative = sampleNegative(vocabulary, exclude, random);
                    if (negative == null) {
                        continue;
                    }
                    updatePair(inputVectors.get(target), outputVectors.get(negative), 0.0, weightedLearningRate);
                }
            }
        }
    }

    private String sampleNegative(List<String> vocabulary, Set<String> exclude, Random random) {
        if (vocabulary.size() <= exclude.size()) {
            return null;
        }
        String candidate;
        do {
            candidate = vocabulary.get(random.nextInt(vocabulary.size()));
        } while (exclude.contains(candidate));
        return candidate;
    }

    private void updatePair(double[] in, double[] out, double label, double learningRate) {
        double prediction = sigmoid(dot(in, out));
        double gradient = (label - prediction) * learningRate;
        for (int d = 0; d < in.length; d++) {
            double inD = in[d];
            double outD = out[d];
            in[d] += gradient * outD;
            out[d] += gradient * inD;
        }
    }

    private double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private double sigmoid(double x) {
        double clipped = Math.max(-6.0, Math.min(6.0, x));
        return 1.0 / (1.0 + Math.exp(-clipped));
    }
}
