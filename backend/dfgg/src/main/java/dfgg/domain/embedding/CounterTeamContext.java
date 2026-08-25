package dfgg.domain.embedding;

import java.util.List;

public record CounterTeamContext(
        String enemyChampionToken,
        List<String> itemTokens,
        boolean win
) {

}
