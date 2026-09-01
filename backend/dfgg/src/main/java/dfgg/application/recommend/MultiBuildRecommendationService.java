package dfgg.application.recommend;

import dfgg.application.champion.ChampionService;
import dfgg.common.CompositionStatsNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.recommendation.BuildCandidate;
import dfgg.domain.recommendation.BuildDirection;
import dfgg.domain.recommendation.ChampionBuildPolicy;
import dfgg.domain.recommendation.CoreBuildCluster;
import dfgg.domain.recommendation.SelectedBuildCandidate;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CombinationContext;
import dfgg.domain.team.Team;
import dfgg.infrastructure.config.RecommendationProperties;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.ItemDto;
import dfgg.presentation.dto.request.RecommendationRequest;
import dfgg.presentation.dto.response.BuildOptionResponse;
import dfgg.presentation.dto.response.MultiBuildRecommendationResponse;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관측 통계를 태그별 빌드 후보로 변환하고 아이템 추천 v2 응답을 조립한다.
 */
@Service
@Transactional(readOnly = true)
public class MultiBuildRecommendationService {

    private static final int NORMAL_BUILD_ITEM_COUNT = 6;
    private static final int BOTTOM_BUILD_ITEM_COUNT = 7;
    private static final int RANKED_SOLO_QUEUE_ID = 420;
    private static final String BOOTS_TAG = "Boots";
    private static final List<String> RECOMMENDATION_TIERS = List.of(
            "EMERALD",
            "DIAMOND",
            "MASTER",
            "GRANDMASTER",
            "CHALLENGER"
    );

    private final ChampionService championService;
    private final ChampionBuildStatsRepository statsRepository;
    private final DataDragonClient dataDragonClient;
    private final RecommendationProperties recommendationProperties;
    private final CoreBuildClusterService clusterService;
    private final BuildCandidateSelectService candidateSelectService;
    private final Map<ChampionTag, ChampionBuildPolicy> policies;

    public MultiBuildRecommendationService(
            ChampionService championService,
            ChampionBuildStatsRepository statsRepository,
            DataDragonClient dataDragonClient,
            RecommendationProperties recommendationProperties,
            CoreBuildClusterService clusterService,
            BuildCandidateSelectService candidateSelectService,
            List<ChampionBuildPolicy> policies
    ) {
        this.championService = championService;
        this.statsRepository = statsRepository;
        this.dataDragonClient = dataDragonClient;
        this.recommendationProperties = recommendationProperties;
        this.clusterService = clusterService;
        this.candidateSelectService = candidateSelectService;
        this.policies = createPolicyMap(policies);
    }

    public MultiBuildRecommendationResponse recommend(RecommendationRequest request) {
        Champion myChampion = championService.findChampionByName(request.myChampion().name());
        ChampionPosition position = ChampionPosition.valueOf(request.myChampion().position());
        List<Champion> allies = resolveChampions(request.allies());
        List<Champion> enemies = resolveChampions(request.enemies());

        CombinationContext context = CombinationContext.analyze(
                new Team(enemies),
                new Team(allies)
        );
        List<ChampionBuildStats> matchingStats = findMatchingStats(
                myChampion,
                position,
                context
        );
        if (matchingStats.isEmpty()) {
            throw new CompositionStatsNotFoundException(
                    myChampion.getName(),
                    position.name()
            );
        }

        List<CoreBuildCluster> clusters = clusterService.groupCoreBuild(matchingStats);
        List<BuildCandidate> candidates = collectCandidates(
                myChampion.getChampionTags(),
                clusters,
                enemies
        );
        int expectedItemCount = expectedItemCount(position);
        List<BuildCandidate> availableCandidates = candidates.stream()
                .filter(candidate -> resolveBuild(
                        candidate,
                        expectedItemCount
                ).isPresent())
                .toList();
        Set<List<Long>> completedClusterKeys = availableCandidates.stream()
                .filter(candidate -> resolveBuild(candidate, expectedItemCount)
                        .filter(build -> isCompleteBuild(build, expectedItemCount))
                        .isPresent())
                .map(candidate -> candidate.cluster().getClusterKey())
                .collect(Collectors.toUnmodifiableSet());
        List<SelectedBuildCandidate> selectedCandidates =
                candidateSelectService.select(
                        availableCandidates,
                        candidate -> completedClusterKeys.contains(
                                candidate.cluster().getClusterKey()
                        )
                );
        List<BuildDirection> supportedDirections = collectSupportedDirections(
                myChampion.getChampionTags()
        );

        return new MultiBuildRecommendationResponse(
                myChampion.getName(),
                position.name(),
                createBuildOptions(
                        selectedCandidates,
                        supportedDirections,
                        expectedItemCount
                )
        );
    }

    private List<Champion> resolveChampions(List<ChampionDto> champions) {
        return champions.stream()
                .map(champion -> championService.findChampionByName(champion.name()))
                .toList();
    }

    private List<ChampionBuildStats> findMatchingStats(
            Champion champion,
            ChampionPosition position,
            CombinationContext context
    ) {
        String latestPatch = dataDragonClient.getLatestVersion();
        List<ChampionBuildStats> latestStats = findMatchingStatsForPatch(
                latestPatch,
                champion,
                position,
                context
        );
        if (totalGameCount(latestStats) >= recommendationProperties.v2MinSampleCount()) {
            return latestStats;
        }

        int[] version = parsePatch(latestPatch);
        return statsRepository.findLatestPatchBefore(
                        version[0],
                        version[1],
                        RANKED_SOLO_QUEUE_ID,
                        RECOMMENDATION_TIERS
                )
                .map(patch -> findMatchingStatsForPatch(
                        patch,
                        champion,
                        position,
                        context
                ))
                .orElseGet(List::of);
    }

    private List<ChampionBuildStats> findMatchingStatsForPatch(
            String patch,
            Champion champion,
            ChampionPosition position,
            CombinationContext context
    ) {
        return statsRepository.findAllMatchingStatsForScope(
                patch,
                RANKED_SOLO_QUEUE_ID,
                RECOMMENDATION_TIERS,
                champion.getChampionId(),
                position.name(),
                context.enemyTankHeavy(),
                context.enemyApHeavy(),
                context.enemyAssassinHeavy(),
                context.allyHasMarksman(),
                context.allyTankHeavy()
        );
    }

    private int totalGameCount(List<ChampionBuildStats> stats) {
        return stats.stream()
                .map(ChampionBuildStats::getGameCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int[] parsePatch(String patch) {
        String[] components = patch.split("\\.");
        if (components.length != 2) {
            throw new IllegalStateException("Latest patch must use major.minor format: " + patch);
        }
        try {
            return new int[]{
                    Integer.parseInt(components[0]),
                    Integer.parseInt(components[1])
            };
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Latest patch must use major.minor format: " + patch, exception);
        }
    }

    private List<BuildCandidate> collectCandidates(
            List<ChampionTag> championTags,
            List<CoreBuildCluster> clusters,
            List<Champion> enemies
    ) {
        List<BuildCandidate> candidates = new ArrayList<>();
        championTags.stream()
                .distinct()
                .map(policies::get)
                .filter(Objects::nonNull)
                .forEach(policy -> candidates.addAll(
                        policy.evaluate(clusters, enemies)
                ));
        return List.copyOf(candidates);
    }

    private Map<ChampionTag, ChampionBuildPolicy> createPolicyMap(
            List<ChampionBuildPolicy> buildPolicies
    ) {
        Objects.requireNonNull(buildPolicies, "빌드 정책 목록은 null일 수 없습니다.");

        Map<ChampionTag, ChampionBuildPolicy> policyMap =
                new EnumMap<>(ChampionTag.class);
        for (ChampionBuildPolicy policy : buildPolicies) {
            Objects.requireNonNull(policy, "빌드 정책은 null일 수 없습니다.");
            ChampionBuildPolicy previous = policyMap.put(
                    policy.supportedTag(),
                    policy
            );
            if (previous != null) {
                throw new IllegalArgumentException(
                        "동일한 챔피언 태그의 빌드 정책을 중복 등록할 수 없습니다: "
                                + policy.supportedTag()
                );
            }
        }
        return Map.copyOf(policyMap);
    }

    private List<BuildDirection> collectSupportedDirections(
            List<ChampionTag> championTags
    ) {
        Set<BuildDirection> directions = new LinkedHashSet<>();
        championTags.stream()
                .distinct()
                .map(policies::get)
                .filter(Objects::nonNull)
                .forEach(policy -> directions.addAll(policy.supportedDirections()));
        return List.copyOf(directions);
    }

    private List<BuildOptionResponse> createBuildOptions(
            List<SelectedBuildCandidate> selectedCandidates,
            List<BuildDirection> supportedDirections,
            int expectedItemCount
    ) {
        List<Optional<List<Item>>> completedBuilds = selectedCandidates.stream()
                .map(selected -> resolveBuild(
                        selected.candidate(),
                        expectedItemCount
                ))
                .toList();
        List<BuildOptionResponse> options = new ArrayList<>(
                BuildCandidateSelectService.MAX_SELECTED_CANDIDATES
        );
        Set<BuildDirection> selectedDirections = new LinkedHashSet<>();
        for (int index = 0; index < selectedCandidates.size(); index++) {
            SelectedBuildCandidate selected = selectedCandidates.get(index);
            Optional<List<Item>> completedBuild = completedBuilds.get(index);
            BuildDirection direction = selected.candidate().direction();
            selectedDirections.add(direction);
            options.add(new BuildOptionResponse(
                    direction.championTag().name(),
                    direction.code(),
                    completedBuild
                            .map(this::toItemDtos)
                            .orElse(null)
            ));
        }

        for (BuildDirection direction : supportedDirections) {
            if (options.size() == BuildCandidateSelectService.MAX_SELECTED_CANDIDATES) {
                break;
            }
            if (selectedDirections.add(direction)) {
                options.add(unavailableOption(direction));
            }
        }
        return List.copyOf(options);
    }

    private BuildOptionResponse unavailableOption(BuildDirection direction) {
        return new BuildOptionResponse(
                direction.championTag().name(),
                direction.code(),
                null
        );
    }

    private Optional<List<Item>> resolveBuild(
            BuildCandidate candidate,
            int expectedItemCount
    ) {
        return candidate.cluster()
                .findOrComposeRepresentativeBuild(expectedItemCount);
    }

    private boolean isCompleteBuild(List<Item> items, int expectedItemCount) {
        return items.size() == expectedItemCount
                && items.stream().filter(item -> item.hasTag(BOOTS_TAG)).count() == 1;
    }

    private int expectedItemCount(ChampionPosition position) {
        return position == ChampionPosition.BOTTOM
                ? BOTTOM_BUILD_ITEM_COUNT
                : NORMAL_BUILD_ITEM_COUNT;
    }

    private List<ItemDto> toItemDtos(List<Item> items) {
        return items.stream()
                .map(ItemDto::from)
                .toList();
    }
}
