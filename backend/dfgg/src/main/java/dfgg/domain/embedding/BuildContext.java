package dfgg.domain.embedding;

import java.util.List;

public record BuildContext(
        String championToken,
        List<String> allyChampionTokens,
        List<String> enemyChampionTokens,
        List<String> itemTokens,
        boolean win
) {

}
