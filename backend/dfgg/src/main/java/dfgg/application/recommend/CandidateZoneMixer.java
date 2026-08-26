package dfgg.application.recommend;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 랭킹된 "80% 안전 구역"/"20% 탐색 구역" 후보를 지정한 비율로 섞는다.
 * 비율(safeZoneRatio)은 호출자가 넘기는 파라미터로, 코드에 고정값으로 박혀있지 않다.
 */
@Component
public class CandidateZoneMixer {

    public MixedCandidates mix(
            List<RankedSequentialPattern> rankedSafeZoneCandidates,
            List<RankedItemCandidate> rankedExplorationZoneCandidates,
            int totalCandidateCount,
            double safeZoneRatio
    ) {
        int safeZoneCount = (int) Math.round(totalCandidateCount * safeZoneRatio);
        int explorationZoneCount = totalCandidateCount - safeZoneCount;

        return new MixedCandidates(
                rankedSafeZoneCandidates.stream().limit(safeZoneCount).toList(),
                rankedExplorationZoneCandidates.stream().limit(explorationZoneCount).toList()
        );
    }
}
