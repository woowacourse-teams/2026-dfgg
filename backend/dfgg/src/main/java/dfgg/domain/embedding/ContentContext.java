package dfgg.domain.embedding;

import java.util.List;

public record ContentContext(
        String itemToken,
        List<String> tagTokens
) {

}