package dfgg.domain.embedding;

import java.util.List;

public record CounterTeamContext(
        List<String> enemyChampionTokens,
        List<String> itemTokens,
        boolean win
) {

}
