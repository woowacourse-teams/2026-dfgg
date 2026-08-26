package dfgg.application.recommend;

public record ItemSimilarityScores(
        Long itemId,
        double cosineToMyChampion,
        double maxSimilarityToAllies,
        double maxSimilarityToEnemies
) {

}
