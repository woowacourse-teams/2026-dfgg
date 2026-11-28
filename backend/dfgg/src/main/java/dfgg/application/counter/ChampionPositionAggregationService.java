package dfgg.application.counter;

import dfgg.domain.counter.ChampionPositionStatsRepository;
import dfgg.domain.match.NormalizedMatchParticipantRepository;
import dfgg.domain.match.PatchVersion;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 패치의 라인 분포를 원본 참가자 데이터에서 집계한다. 현재 패치는 실행할 때마다 다시 정하고,
 * 그 패치 행을 지운 뒤 새로 넣으므로 다시 돌려도 결과가 같다.
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
        statsRepository.deleteByPatch(patch);
        return new ChampionPositionAggregationResult(patch, statsRepository.aggregateFrom(patch));
    }
}
