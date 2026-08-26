package dfgg.application.recommend;

import dfgg.application.champion.ChampionService;
import dfgg.common.CompositionStatsNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.recommendation.BuildCandidate;
import dfgg.domain.recommendation.ChampionBuildPolicy;
import dfgg.domain.recommendation.CoreBuildCluster;
import dfgg.domain.recommendation.SelectedBuildCandidate;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.domain.stats.CombinationContext;
import dfgg.domain.team.Team;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.ItemDto;
import dfgg.presentation.dto.request.RecommendationRequest;
import dfgg.presentation.dto.response.BuildOptionResponse;
import dfgg.presentation.dto.response.MultiBuildRecommendationResponse;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private final ChampionService championService;
    private final ChampionBuildStatsRepository statsRepository;
    private final CoreBuildClusterService clusterService;
    private final BuildCandidateSelectService candidateSelectService;
    private final Map<ChampionTag, ChampionBuildPolicy> policies;

    public MultiBuildRecommendationService(
            ChampionService championService,
            ChampionBuildStatsRepository statsRepository,
            CoreBuildClusterService clusterService,
            BuildCandidateSelectService candidateSelectService,
            List<ChampionBuildPolicy> policies
    ) {
        this.championService = championService;
        this.statsRepository = statsRepository;
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
        List<SelectedBuildCandidate> selectedCandidates =
                candidateSelectService.select(candidates);

        return new MultiBuildRecommendationResponse(
                myChampion.getName(),
                position.name(),
                createBuildOptions(selectedCandidates, position)
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
        return statsRepository.findAllMatchingStats(
                champion.getChampionId(),
                position.name(),
                context.enemyTankHeavy(),
                context.enemyApHeavy(),
                context.enemyAssassinHeavy(),
                context.allyHasMarksman(),
                context.allyTankHeavy()
        );
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

    private List<BuildOptionResponse> createBuildOptions(
            List<SelectedBuildCandidate> selectedCandidates,
            ChampionPosition position
    ) {
        int expectedItemCount = position == ChampionPosition.BOTTOM
                ? BOTTOM_BUILD_ITEM_COUNT
                : NORMAL_BUILD_ITEM_COUNT;
        List<Optional<ChampionBuildStats>> completedBuilds = selectedCandidates.stream()
                .map(selected -> selected.candidate().cluster()
                        .findRepresentativeBuild(expectedItemCount)
                        .filter(this::hasExactlyOneBoots))
                .toList();
        int recommendedIndex = findRecommendedAvailableIndex(
                selectedCandidates,
                completedBuilds
        );

        List<BuildOptionResponse> options = new ArrayList<>(selectedCandidates.size());
        for (int index = 0; index < selectedCandidates.size(); index++) {
            SelectedBuildCandidate selected = selectedCandidates.get(index);
            Optional<ChampionBuildStats> completedBuild = completedBuilds.get(index);
            options.add(new BuildOptionResponse(
                    selected.candidate().direction().championTag().name(),
                    selected.candidate().direction().code(),
                    completedBuild.isPresent(),
                    index == recommendedIndex,
                    completedBuild
                            .map(this::toItemDtos)
                            .orElse(null)
            ));
        }
        return List.copyOf(options);
    }

    private int findRecommendedAvailableIndex(
            List<SelectedBuildCandidate> selectedCandidates,
            List<Optional<ChampionBuildStats>> completedBuilds
    ) {
        int recommendedIndex = -1;
        for (int index = 0; index < selectedCandidates.size(); index++) {
            if (completedBuilds.get(index).isEmpty()) {
                continue;
            }
            if (recommendedIndex < 0
                    || selectedCandidates.get(index).candidate().suitabilityScore()
                    > selectedCandidates.get(recommendedIndex).candidate().suitabilityScore()) {
                recommendedIndex = index;
            }
        }
        return recommendedIndex;
    }

    private boolean hasExactlyOneBoots(ChampionBuildStats stats) {
        return stats.getItems().stream()
                .filter(item -> item.hasTag("Boots"))
                .count() == 1;
    }

    private List<ItemDto> toItemDtos(ChampionBuildStats stats) {
        return stats.getItems().stream()
                .map(ItemDto::from)
                .toList();
    }
}
