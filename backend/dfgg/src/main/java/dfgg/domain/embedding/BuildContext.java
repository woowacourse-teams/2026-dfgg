package dfgg.domain.embedding;

import java.util.List;

public record BuildContext(
        String championToken,
        List<String> itemTokens,
        boolean win
) {

}
