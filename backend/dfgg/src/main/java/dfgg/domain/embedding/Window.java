package dfgg.domain.embedding;

import java.util.List;

public record Window(
        List<String> tokens,
        double weight
) {

    public Window(List<String> tokens) {
        this(tokens, 1.0);
    }
}
