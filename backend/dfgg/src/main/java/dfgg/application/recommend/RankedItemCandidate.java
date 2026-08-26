package dfgg.application.recommend;

public record RankedItemCandidate(
        Long itemId,
        double maxSimilarity
) {

}
