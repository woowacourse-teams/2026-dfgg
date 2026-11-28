package dfgg.application.counter;

import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.counter.ChampionPositionStats;
import dfgg.domain.counter.ChampionPositionStatsRepository;
import dfgg.domain.counter.PositionDistribution;
import dfgg.domain.match.PatchVersion;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 집계된 최근 패치의 챔피언별 라인 분포를 읽는다. 밴픽 상대 라인 예측과 훈련모드 주 라인 추정이 같이 쓴다. */
@Component
@Transactional(readOnly = true)
public class ChampionPositionReader {

    private final ChampionPositionStatsRepository repository;

    public ChampionPositionReader(ChampionPositionStatsRepository repository) {
        this.repository = repository;
    }

    /** 라인 분포를 집계한 패치 중 가장 높은 패치. 집계가 없으면 비어 있다. */
    public Optional<PatchVersion> latestPatch() {
        return PatchVersion.latest(repository.findDistinctPatches());
    }

    /** 기록이 없는 챔피언은 결과에 없다. */
    public Map<Integer, PositionDistribution> distributionsOf(PatchVersion patch, Collection<Integer> championIds) {
        return repository.findByPatchAndChampionIdIn(patch.value(), championIds).stream()
                .collect(Collectors.groupingBy(ChampionPositionStats::getChampionId,
                        Collectors.collectingAndThen(
                                Collectors.toMap(ChampionPositionStats::getPosition, ChampionPositionStats::getGames,
                                        Integer::sum, () -> new EnumMap<>(ChampionPosition.class)),
                                PositionDistribution::of)));
    }

    /** 최근 패치에서 가장 많이 간 라인. 집계나 그 챔피언의 기록이 없으면 비어 있다. */
    public Optional<ChampionPosition> mainPositionOf(long championId) {
        int id = Math.toIntExact(championId);
        return latestPatch()
                .map(patch -> distributionsOf(patch, List.of(id)))
                .map(distributions -> distributions.get(id))
                .flatMap(PositionDistribution::mostPlayed);
    }
}
