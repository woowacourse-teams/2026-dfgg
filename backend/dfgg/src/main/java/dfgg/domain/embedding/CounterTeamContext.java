package dfgg.domain.embedding;

public record CounterTeamContext(
        String enemyChampionToken,
        String itemToken,
        boolean win,
        double itemFrequencyWeight
) {

}
