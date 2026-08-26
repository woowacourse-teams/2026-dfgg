package dfgg.application.recommend;

import java.util.List;

public record MixedCandidates(
        List<RankedItemCandidate> safeZoneCandidates,
        List<RankedItemCandidate> explorationZoneCandidates
) {

}
