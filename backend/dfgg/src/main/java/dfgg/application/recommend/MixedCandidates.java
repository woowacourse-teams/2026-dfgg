package dfgg.application.recommend;

import java.util.List;

public record MixedCandidates(
        List<RankedSequentialPattern> safeZoneCandidates,
        List<RankedItemCandidate> explorationZoneCandidates
) {

}
