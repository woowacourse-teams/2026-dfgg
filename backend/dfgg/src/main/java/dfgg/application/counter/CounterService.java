package dfgg.application.counter;

import dfgg.common.exception.ChampionNotFoundException;
import dfgg.common.exception.InvalidRecommendationRequestException;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.counter.ChampionCounter;
import dfgg.domain.counter.ChampionCounterRepository;
import dfgg.domain.counter.ChampionPositionStats;
import dfgg.domain.counter.ChampionPositionStatsRepository;
import dfgg.domain.counter.PositionDistribution;
import dfgg.domain.language.Language;
import dfgg.domain.match.PatchVersion;
import dfgg.presentation.dto.request.CounterRequest;
import dfgg.presentation.dto.response.CounterResponse;
import dfgg.presentation.dto.response.CounterResponse.CounterPickResponse;
import dfgg.presentation.dto.response.CounterResponse.EnemyCounterResponse;
import dfgg.presentation.dto.response.CounterResponse.PositionShareResponse;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상대 공개 픽마다 현재 패치 라인 분포와, 미리 넣어 둔 (그 적, 내 포지션) 카운터를 돌려준다.
 */
@Service
public class CounterService {

    private final ChampionPositionStatsRepository positionStatsRepository;
    private final ChampionCounterRepository counterRepository;
    private final ChampionRepository championRepository;
    private final int picksPerEnemy;

    public CounterService(
            ChampionPositionStatsRepository positionStatsRepository,
            ChampionCounterRepository counterRepository,
            ChampionRepository championRepository,
            @Value("${counter.picks-per-enemy}") int picksPerEnemy
    ) {
        this.positionStatsRepository = positionStatsRepository;
        this.counterRepository = counterRepository;
        this.championRepository = championRepository;
        this.picksPerEnemy = picksPerEnemy;
    }

    @Transactional(readOnly = true)
    public CounterResponse recommend(CounterRequest request, Language language) {
        validateChampions(request);
        Optional<PatchVersion> patch = PatchVersion.latest(positionStatsRepository.findDistinctPatches());
        Map<Integer, Map<ChampionPosition, Integer>> gamesByEnemy = gamesByEnemy(patch, request.enemies());
        Map<Integer, List<Integer>> countersByEnemy = countersByEnemy(request);
        Map<Integer, String> names = namesOf(request.enemies(), countersByEnemy, language);

        List<EnemyCounterResponse> enemies = request.enemies().stream()
                .map(enemyId -> new EnemyCounterResponse(
                        enemyId,
                        names.get(enemyId),
                        positionsOf(gamesByEnemy.getOrDefault(enemyId, Map.of())),
                        countersOf(countersByEnemy.get(enemyId), names)))
                .toList();
        return new CounterResponse(patch.map(PatchVersion::value).orElse(null), request.myPosition(), enemies);
    }

    /** 같은 챔피언이 아군·적·밴에 두 번 나오거나, 등록되지 않은 챔피언이 있으면 거부한다. */
    private void validateChampions(CounterRequest request) {
        List<Integer> requested = Stream.of(request.enemies(), request.allies(), request.bans())
                .flatMap(Collection::stream)
                .toList();
        Set<Integer> distinct = new HashSet<>(requested);
        if (distinct.size() != requested.size()) {
            throw new InvalidRecommendationRequestException("같은 챔피언이 아군·적·밴에 두 번 이상 있습니다: " + duplicatesOf(requested));
        }
        Set<Integer> known = championRepository.findAllById(distinct.stream().map(Integer::longValue).toList())
                .stream()
                .map(champion -> champion.getChampionId().intValue())
                .collect(Collectors.toSet());
        List<Integer> unknown = requested.stream().filter(championId -> !known.contains(championId)).toList();
        if (!unknown.isEmpty()) {
            throw new ChampionNotFoundException(unknown.toString());
        }
    }

    private List<Integer> duplicatesOf(List<Integer> championIds) {
        Set<Integer> seen = new HashSet<>();
        return championIds.stream().filter(championId -> !seen.add(championId)).distinct().toList();
    }

    private Map<Integer, Map<ChampionPosition, Integer>> gamesByEnemy(
            Optional<PatchVersion> patch, List<Integer> enemyIds) {
        if (patch.isEmpty()) {
            return Map.of();
        }
        return positionStatsRepository.findByPatchAndChampionIdIn(patch.get().value(), enemyIds).stream()
                .collect(Collectors.groupingBy(ChampionPositionStats::getChampionId,
                        Collectors.toMap(ChampionPositionStats::getPosition, ChampionPositionStats::getGames,
                                Integer::sum, () -> new EnumMap<>(ChampionPosition.class))));
    }

    private List<PositionShareResponse> positionsOf(Map<ChampionPosition, Integer> games) {
        return PositionDistribution.of(games).shares().stream()
                .map(share -> new PositionShareResponse(share.position(), share.rate(), share.games()))
                .toList();
    }

    /** 아군·적·밴에 있는 챔피언은 빼고 저장 순서대로 앞의 몇 개를 고른다. */
    private Map<Integer, List<Integer>> countersByEnemy(CounterRequest request) {
        Set<Integer> taken = takenChampions(request);
        return request.enemies().stream()
                .distinct()
                .collect(Collectors.toMap(Function.identity(), enemyId -> counterRepository
                        .findByEnemyChampionIdAndPositionOrderByRankAsc(enemyId, request.myPosition()).stream()
                        .map(ChampionCounter::getCounterChampionId)
                        .filter(counterId -> !taken.contains(counterId))
                        .limit(picksPerEnemy)
                        .toList()));
    }

    private Set<Integer> takenChampions(CounterRequest request) {
        Set<Integer> taken = new HashSet<>(request.enemies());
        taken.addAll(request.allies());
        taken.addAll(request.bans());
        return taken;
    }

    private List<CounterPickResponse> countersOf(List<Integer> counterIds, Map<Integer, String> names) {
        return counterIds.stream()
                .map(counterId -> new CounterPickResponse(counterId, names.get(counterId), null))
                .toList();
    }

    private Map<Integer, String> namesOf(
            List<Integer> enemyIds, Map<Integer, List<Integer>> countersByEnemy, Language language) {
        Set<Long> championIds = Stream.concat(enemyIds.stream(),
                        countersByEnemy.values().stream().flatMap(Collection::stream))
                .map(Integer::longValue)
                .collect(Collectors.toSet());
        return championRepository.findAllById(championIds).stream()
                .collect(Collectors.toMap(champion -> champion.getChampionId().intValue(),
                        champion -> language.pick(champion.getName())));
    }
}
