package dfgg.application.stats;

import dfgg.domain.match.NormalizedMatch;
import dfgg.domain.match.NormalizedParticipant;
import dfgg.domain.stats.StatsAggregationCompletionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정상 수집 경로에서 정규화된 매치의 통계를 등록하고 중복 집계를 막는다.
 */
@Service
public class ChampionBuildStatsMatchService {

    private static final String QUEUE_TYPE = "RANKED_SOLO_5x5";
    private static final String AGGREGATION_REVISION = "v1";

    private final ChampionBuildStatsAggregationService aggregationService;
    private final StatsAggregationCompletionRepository completionRepository;

    public ChampionBuildStatsMatchService(
            ChampionBuildStatsAggregationService aggregationService,
            StatsAggregationCompletionRepository completionRepository
    ) {
        this.aggregationService = aggregationService;
        this.completionRepository = completionRepository;
    }

    /**
     * 정규화 직후 전달받은 매치에서 지정한 티어의 통계를 등록한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerMatchStats(NormalizedMatch normalized, String tier) {
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("tier must not be blank");
        }
        List<String> participantPuuids = normalized.participants().stream()
                .filter(participant -> tier.equals(participant.tier()))
                .map(NormalizedParticipant::puuid)
                .toList();
        if (participantPuuids.isEmpty()) {
            return;
        }
        // 참가자별 completion 유니크 제약으로 일반 집계의 중복 선점을 막는다.
        List<String> claimedPuuids = claimPendingPuuids(
                normalized.matchId(),
                participantPuuids,
                QUEUE_TYPE,
                tier,
                AGGREGATION_REVISION
        );
        if (claimedPuuids.isEmpty()) {
            return;
        }

        aggregationService.aggregate(normalized, tier, claimedPuuids);
    }

    /**
     * 지정된 참가자 중 completion 행을 새로 만든 참가자만 선점 성공 목록으로 반환한다.
     */
    private List<String> claimPendingPuuids(
            String matchId,
            Collection<String> participantPuuids,
            String queueType,
            String tier,
            String aggregationRevision
    ) {
        List<String> claimedPuuids = new ArrayList<>();
        for (String puuid : participantPuuids.stream().distinct().sorted().toList()) {
            int inserted = completionRepository.insertIfAbsent(
                    matchId,
                    puuid,
                    queueType,
                    tier,
                    aggregationRevision
            );
            if (inserted == 1) {
                claimedPuuids.add(puuid);
            }
        }
        return List.copyOf(claimedPuuids);
    }

}
