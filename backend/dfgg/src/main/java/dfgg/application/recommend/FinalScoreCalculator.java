package dfgg.application.recommend;

import org.springframework.stereotype.Component;

/**
 * late-interaction 재정렬 공식을 계산한다.
 * finalScore = w1*wilsonLowerBound + w2*cosine(item,내챔피언) + w3*maxSim(item,아군) + w4*maxSim(item,적)
 */
@Component
public class FinalScoreCalculator {

    public double calculate(
            double wilsonLowerBound,
            double cosineToMyChampion,
            double maxSimilarityToAllies,
            double maxSimilarityToEnemies,
            FinalScoreWeights weights
    ) {
        return weights.wilsonWeight() * wilsonLowerBound
                + weights.myChampionWeight() * cosineToMyChampion
                + weights.allyWeight() * maxSimilarityToAllies
                + weights.enemyWeight() * maxSimilarityToEnemies;
    }
}
