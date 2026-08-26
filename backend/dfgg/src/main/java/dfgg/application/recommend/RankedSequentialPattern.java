package dfgg.application.recommend;

import dfgg.domain.sequence.MinedSequentialPattern;

public record RankedSequentialPattern(
        MinedSequentialPattern pattern,
        double wilsonLowerBound
) {

}
