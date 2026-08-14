package dfgg.domain.embedding;

import java.util.List;

public record CounterContext(
        List<String> enemyChampionTokens,
        List<String> itemTokens
) {

}