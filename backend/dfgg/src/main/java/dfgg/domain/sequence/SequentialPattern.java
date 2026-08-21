package dfgg.domain.sequence;

import java.util.List;

public record SequentialPattern(
        List<Long> items,
        int support
) {

}
