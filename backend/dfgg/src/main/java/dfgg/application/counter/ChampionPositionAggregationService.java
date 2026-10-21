package dfgg.application.counter;

import dfgg.domain.counter.ChampionPositionStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.PatchVersion;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 패치의 라인 분포를 원본 참가자 데이터에서 집계한다. 현재 패치는 실행할 때마다 다시 정한다.
 */
@Service
public class ChampionPositionAggregationService {

    private final NormalizedMatchParticipantRepository participantRepository;
    private final ChampionPositionStatsRepository statsRepository;

    public ChampionPositionAggregationService(
            NormalizedMatchParticipantRepository participantRepository,
            ChampionPositionStatsRepository statsRepository
    ) {
        this.participantRepository = participantRepository;
        this.statsRepository = statsRepository;
    }

    @Transactional
    public ChampionPositionAggregationResult aggregate() {
        Optional<PatchVersion> currentPatch = PatchVersion.latest(participantRepository.findDistinctPatches());
        if (currentPatch.isEmpty()) {
            return ChampionPositionAggregationResult.empty();
        }
        String patch = currentPatch.get().value();
        return new ChampionPositionAggregationResult(patch, statsRepository.aggregateFrom(patch));
    }
}
