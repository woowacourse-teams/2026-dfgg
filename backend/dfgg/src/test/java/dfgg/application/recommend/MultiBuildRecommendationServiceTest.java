package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dfgg.application.champion.ChampionService;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionTag;
import dfgg.domain.item.Item;
import dfgg.domain.recommendation.AssassinBuildPolicy;
import dfgg.domain.recommendation.ChampionBuildPolicy;
import dfgg.domain.recommendation.FighterBuildPolicy;
import dfgg.domain.recommendation.CoreBuildCluster;
import dfgg.domain.recommendation.MageBuildPolicy;
import dfgg.domain.recommendation.MarksmanBuildPolicy;
import dfgg.domain.recommendation.SupportBuildPolicy;
import dfgg.domain.recommendation.TankBuildPolicy;
import dfgg.domain.stats.ChampionBuildStats;
import dfgg.domain.stats.ChampionBuildStatsRepository;
import dfgg.infrastructure.config.RecommendationProperties;
import dfgg.infrastructure.external.client.DataDragonClient;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.request.RecommendationRequest;
import dfgg.presentation.dto.response.BuildOptionResponse;
import dfgg.presentation.dto.response.MultiBuildRecommendationResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultiBuildRecommendationServiceTest {

    private static final List<String> RECOMMENDATION_TIERS = List.of(
            "EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER"
    );

    @Mock
    private ChampionService championService;

    @Mock
    private ChampionBuildStatsRepository statsRepository;

    @Mock
    private DataDragonClient dataDragonClient;

    private MultiBuildRecommendationService recommendationService;
    private Champion myChampion;

    @BeforeEach
    void setUp() {
        recommendationService = new MultiBuildRecommendationService(
                championService,
                statsRepository,
                dataDragonClient,
                recommendationProperties(1),
                new CoreBuildClusterService(),
                new BuildCandidateSelectService(),
                buildPolicies()
        );
        myChampion = new Champion(
                1L,
                "Malphite",
                "말파이트",
                List.of(ChampionTag.TANK)
        );
    }

    @Test
    @DisplayName("일반 포지션은 신발을 포함한 실제 관측 아이템 6개를 반환한다")
    void recommend_NormalPosition_ReturnsSixObservedItems() {
        // given
        RecommendationRequest request = prepareRequest(ChampionPosition.TOP);
        ChampionBuildStats completeBuild = stats(
                ChampionPosition.TOP,
                "NORMAL_COMPLETE",
                completeItems(6),
                30
        );
        givenMatchingStats(ChampionPosition.TOP, List.of(completeBuild));

        // when
        MultiBuildRecommendationResponse response = recommendationService.recommend(request);

        // then
        assertThat(response.builds()).hasSize(3);
        assertThat(response.builds())
                .extracting(BuildOptionResponse::direction)
                .containsExactly(
                        "PHYSICAL_DAMAGE",
                        "MAGIC_DAMAGE",
                        "MIXED_DAMAGE"
                );
        assertThat(response.builds().getFirst().build()).hasSize(6);
        assertThat(response.builds().subList(1, 3))
                .allSatisfy(option -> assertThat(option.build()).isNull());
    }

    @Test
    @DisplayName("BOTTOM 포지션은 신발을 포함한 실제 관측 아이템 7개를 반환한다")
    void recommend_BottomPosition_ReturnsSevenObservedItems() {
        // given
        RecommendationRequest request = prepareRequest(ChampionPosition.BOTTOM);
        ChampionBuildStats completeBuild = stats(
                ChampionPosition.BOTTOM,
                "BOTTOM_COMPLETE",
                completeItems(7),
                30
        );
        givenMatchingStats(ChampionPosition.BOTTOM, List.of(completeBuild));

        // when
        MultiBuildRecommendationResponse response = recommendationService.recommend(request);

        // then
        assertThat(response.builds()).hasSize(3);
        assertThat(response.builds().getFirst().build()).hasSize(7);
    }

    @Test
    @DisplayName("완성된 관측 빌드가 없으면 3코어 빌드를 반환한다")
    void recommend_WhenCompletedBuildDoesNotExist_ReturnsThreeCoreBuild() {
        // given
        RecommendationRequest request = prepareRequest(ChampionPosition.TOP);
        ChampionBuildStats incompleteBuild = stats(
                ChampionPosition.TOP,
                "INCOMPLETE",
                List.of(
                        armorItem(101L),
                        armorItem(102L),
                        armorItem(103L)
                ),
                30
        );
        givenMatchingStats(ChampionPosition.TOP, List.of(incompleteBuild));

        // when
        MultiBuildRecommendationResponse response = recommendationService.recommend(request);

        // then
        assertThat(response.builds())
                .extracting(BuildOptionResponse::direction)
                .containsExactly(
                        "PHYSICAL_DAMAGE",
                        "MAGIC_DAMAGE",
                        "MIXED_DAMAGE"
        );
        assertThat(response.builds().getFirst().build()).hasSize(3);
        assertThat(response.builds().subList(1, 3))
                .allSatisfy(option -> assertThat(option.build()).isNull());
    }

    @Test
    @DisplayName("완성 buildKey가 없어도 같은 군집의 후반 통계로 6개를 보충한다")
    void recommend_WhenCompletedBuildDoesNotExist_ComposesFromClusterStats() {
        // given
        RecommendationRequest request = prepareRequest(ChampionPosition.TOP);
        Item firstCore = armorItem(151L);
        Item secondCore = armorItem(152L);
        Item thirdCore = armorItem(153L);
        Item boots = new Item(154L, "신발", List.of("Boots"));
        Item fourthItem = new Item(155L, "네 번째 아이템");
        Item fifthItem = new Item(156L, "다섯 번째 아이템");
        ChampionBuildStats firstPartial = stats(
                ChampionPosition.TOP,
                "FIRST_PARTIAL",
                List.of(
                        firstCore,
                        boots,
                        secondCore,
                        thirdCore,
                        fourthItem
                ),
                20
        );
        ChampionBuildStats secondPartial = stats(
                ChampionPosition.TOP,
                "SECOND_PARTIAL",
                List.of(
                        firstCore,
                        secondCore,
                        thirdCore,
                        fourthItem,
                        fifthItem
                ),
                10
        );
        givenMatchingStats(
                ChampionPosition.TOP,
                List.of(firstPartial, secondPartial)
        );

        // when
        MultiBuildRecommendationResponse response = recommendationService.recommend(request);

        // then
        BuildOptionResponse physicalBuild = response.builds().getFirst();
        assertThat(physicalBuild.direction()).isEqualTo("PHYSICAL_DAMAGE");
        assertThat(physicalBuild.build())
                .extracting(item -> item.name())
                .containsExactly(
                        "방어 아이템 151",
                        "신발",
                        "방어 아이템 152",
                        "방어 아이템 153",
                        "네 번째 아이템",
                        "다섯 번째 아이템"
                );
    }

    @Test
    @DisplayName("완성 빌드를 먼저 추천하고 남는 슬롯에는 미완성 빌드도 포함한다")
    void recommend_WhenCompleteAndPartialBuildsExist_PrioritizesCompleteBuild() {
        // given
        RecommendationRequest request = prepareRequest(ChampionPosition.TOP);
        ChampionBuildStats highScoreIncomplete = stats(
                ChampionPosition.TOP,
                "HIGH_INCOMPLETE",
                List.of(
                        new Item(201L, "고득점 방어 아이템 201", List.of("Armor", "Health")),
                        new Item(202L, "고득점 방어 아이템 202", List.of("Armor", "Health")),
                        new Item(203L, "고득점 방어 아이템 203", List.of("Armor", "Health"))
                ),
                100
        );
        ChampionBuildStats lowerScoreComplete = stats(
                ChampionPosition.TOP,
                "LOW_COMPLETE",
                List.of(
                        new Item(301L, "마법 방어 아이템 301", List.of("SpellBlock")),
                        new Item(302L, "마법 방어 아이템 302", List.of("SpellBlock")),
                        new Item(303L, "마법 방어 아이템 303", List.of("SpellBlock")),
                        new Item(304L, "신발", List.of("Boots")),
                        new Item(305L, "후반 아이템 4"),
                        new Item(306L, "후반 아이템 5")
                ),
                10
        );
        givenMatchingStats(
                ChampionPosition.TOP,
                List.of(highScoreIncomplete, lowerScoreComplete)
        );

        // when
        MultiBuildRecommendationResponse response = recommendationService.recommend(request);

        // then
        assertThat(response.builds()).hasSize(3);
        assertThat(response.builds().get(0).build()).hasSize(6);
        assertThat(response.builds().get(1).build()).hasSize(3);
        assertThat(response.builds().get(2).build()).isNull();
        assertThat(response.builds())
                .extracting(BuildOptionResponse::direction)
                .containsExactly(
                        "MAGIC_DAMAGE",
                        "PHYSICAL_DAMAGE",
                        "MIXED_DAMAGE"
                );
    }

    @Test
    @DisplayName("내 챔피언의 모든 태그에 등록된 공통 정책을 실행한다")
    void recommend_EvaluatesPoliciesForAllChampionTags() {
        // given
        myChampion = new Champion(
                1L,
                "Galio",
                "갈리오",
                List.of(ChampionTag.TANK, ChampionTag.MAGE)
        );
        ChampionBuildPolicy tankPolicy = policy(ChampionTag.TANK);
        ChampionBuildPolicy magePolicy = policy(ChampionTag.MAGE);
        ChampionBuildPolicy fighterPolicy = policy(ChampionTag.FIGHTER);
        CoreBuildClusterService clusterService = mock(CoreBuildClusterService.class);
        List<CoreBuildCluster> clusters = List.of();
        recommendationService = new MultiBuildRecommendationService(
                championService,
                statsRepository,
                dataDragonClient,
                recommendationProperties(1),
                clusterService,
                new BuildCandidateSelectService(),
                List.of(tankPolicy, magePolicy, fighterPolicy)
        );
        RecommendationRequest request = prepareRequest(ChampionPosition.MID);
        ChampionBuildStats observedStats = stats(
                ChampionPosition.MID,
                "OBSERVED",
                completeItems(6),
                10
        );
        givenMatchingStats(ChampionPosition.MID, List.of(observedStats));
        given(clusterService.groupCoreBuild(List.of(observedStats)))
                .willReturn(clusters);

        // when
        recommendationService.recommend(request);

        // then
        verify(tankPolicy).evaluate(eq(clusters), anyList());
        verify(magePolicy).evaluate(eq(clusters), anyList());
        verify(fighterPolicy, never()).evaluate(anyList(), anyList());
    }

    @Test
    @DisplayName("다중 태그 챔피언의 동일 군집은 정규화된 적합도로 정책을 선택한다")
    void recommend_MultiTagChampion_SelectsPolicyByNormalizedSuitability() {
        // given
        myChampion = new Champion(
                1L,
                "TwistedFate",
                "트위스티드 페이트",
                List.of(ChampionTag.MAGE, ChampionTag.MARKSMAN)
        );
        RecommendationRequest request = prepareRequest(ChampionPosition.MID);
        List<Item> hybridBuild = List.of(
                new Item(401L, "성장 아이템", List.of("SpellDamage", "Mana", "Health")),
                new Item(
                        402L,
                        "가속 아이템",
                        List.of("SpellDamage", "AbilityHaste", "NonbootsMovement")
                ),
                new Item(403L, "유틸 아이템", List.of("SpellDamage", "Mana", "Slow")),
                new Item(404L, "신발", List.of("Boots")),
                new Item(405L, "후반 아이템 A"),
                new Item(406L, "후반 아이템 B")
        );
        ChampionBuildStats observedBuild = stats(
                ChampionPosition.MID,
                "HYBRID_COMPLETE",
                hybridBuild,
                30
        );
        givenMatchingStats(ChampionPosition.MID, List.of(observedBuild));

        // when
        MultiBuildRecommendationResponse response = recommendationService.recommend(request);

        // then
        assertThat(response.builds().getFirst())
                .satisfies(build -> {
                    assertThat(build.championTag()).isEqualTo("MAGE");
                    assertThat(build.direction()).isEqualTo("SUSTAINED_DAMAGE");
                    assertThat(build.build()).hasSize(6);
                });
    }

    @Test
    @DisplayName("다중 태그 챔피언의 일반 원딜 빌드는 MARKSMAN 정책을 유지한다")
    void recommend_MultiTagChampion_KeepsMarksmanPolicyForCriticalStrikeBuild() {
        // given
        myChampion = new Champion(
                1L,
                "Kaisa",
                "카이사",
                List.of(ChampionTag.MAGE, ChampionTag.MARKSMAN)
        );
        RecommendationRequest request = prepareRequest(ChampionPosition.BOTTOM);
        List<Item> criticalStrikeBuild = List.of(
                new Item(
                        501L,
                        "치명타 주문 아이템",
                        List.of(
                                "SpellDamage",
                                "MagicPenetration",
                                "CriticalStrike",
                                "Damage",
                                "AttackSpeed",
                                "ArmorPenetration"
                        )
                ),
                new Item(
                        502L,
                        "치명타 아이템 A",
                        List.of("CriticalStrike", "Damage", "AttackSpeed", "ArmorPenetration")
                ),
                new Item(
                        503L,
                        "치명타 아이템 B",
                        List.of("CriticalStrike", "Damage", "AttackSpeed", "ArmorPenetration")
                ),
                new Item(504L, "신발", List.of("Boots")),
                new Item(505L, "후반 아이템 A"),
                new Item(506L, "후반 아이템 B"),
                new Item(507L, "후반 아이템 C")
        );
        ChampionBuildStats observedBuild = stats(
                ChampionPosition.BOTTOM,
                "CRITICAL_STRIKE_COMPLETE",
                criticalStrikeBuild,
                30
        );
        givenMatchingStats(ChampionPosition.BOTTOM, List.of(observedBuild));

        // when
        MultiBuildRecommendationResponse response = recommendationService.recommend(request);

        // then
        assertThat(response.builds().getFirst())
                .satisfies(build -> {
                    assertThat(build.championTag()).isEqualTo("MARKSMAN");
                    assertThat(build.direction()).isEqualTo("CRITICAL_STRIKE_DAMAGE");
                    assertThat(build.build()).hasSize(7);
                });
    }

    @Test
    @DisplayName("최신 패치의 표본 수가 기준 이상이면 최신 패치 통계를 사용한다")
    void recommend_WhenLatestPatchHasEnoughSamples_UsesLatestPatchStats() {
        recommendationService = new MultiBuildRecommendationService(
                championService,
                statsRepository,
                dataDragonClient,
                recommendationProperties(30),
                new CoreBuildClusterService(),
                new BuildCandidateSelectService(),
                buildPolicies()
        );
        RecommendationRequest request = prepareRequest(ChampionPosition.TOP);
        ChampionBuildStats latestStats = stats(
                ChampionPosition.TOP,
                "LATEST",
                completeItems(6),
                30
        );
        givenMatchingStats(ChampionPosition.TOP, List.of(latestStats));

        MultiBuildRecommendationResponse response = recommendationService.recommend(request);

        assertThat(response.builds().getFirst().build()).hasSize(6);
        verify(statsRepository, never()).findLatestPatchBefore(
                16,
                17,
                420,
                RECOMMENDATION_TIERS
        );
    }

    @Test
    @DisplayName("최신 패치의 표본 수가 기준 미만이면 직전 패치 통계를 사용한다")
    void recommend_WhenLatestPatchHasInsufficientSamples_UsesPreviousPatchStats() {
        recommendationService = new MultiBuildRecommendationService(
                championService,
                statsRepository,
                dataDragonClient,
                recommendationProperties(30),
                new CoreBuildClusterService(),
                new BuildCandidateSelectService(),
                buildPolicies()
        );
        RecommendationRequest request = prepareRequest(ChampionPosition.TOP);
        ChampionBuildStats latestStats = stats(
                ChampionPosition.TOP,
                "LATEST",
                completeItems(6),
                29
        );
        ChampionBuildStats previousStats = stats(
                ChampionPosition.TOP,
                "PREVIOUS",
                completeItems(6, 101L),
                40
        );
        given(dataDragonClient.getLatestVersion()).willReturn("16.17");
        givenMatchingStats("16.17", ChampionPosition.TOP, List.of(latestStats));
        given(statsRepository.findLatestPatchBefore(
                16,
                17,
                420,
                RECOMMENDATION_TIERS
        )).willReturn(Optional.of("16.16"));
        givenMatchingStats("16.16", ChampionPosition.TOP, List.of(previousStats));

        MultiBuildRecommendationResponse response = recommendationService.recommend(request);

        assertThat(response.builds().getFirst().build())
                .extracting(item -> item.id())
                .containsExactly(101L, 102L, 103L, 104L, 105L, 106L);
    }

    private RecommendationRequest prepareRequest(ChampionPosition position) {
        Champion ally = champion(2L, "아군", ChampionTag.MARKSMAN);
        Champion enemy = champion(3L, "적군", ChampionTag.FIGHTER);
        given(championService.findChampionByName("말파이트")).willReturn(myChampion);
        given(championService.findChampionByName("아군")).willReturn(ally);
        given(championService.findChampionByName("적군")).willReturn(enemy);

        return new RecommendationRequest(
                new ChampionDto("말파이트", position.name()),
                List.of(new ChampionDto("아군", "BOTTOM")),
                List.of(new ChampionDto("적군", "TOP"))
        );
    }

    private void givenMatchingStats(
            ChampionPosition position,
            List<ChampionBuildStats> stats
    ) {
        given(dataDragonClient.getLatestVersion()).willReturn("16.17");
        givenMatchingStats("16.17", position, stats);
    }

    private void givenMatchingStats(
            String patch,
            ChampionPosition position,
            List<ChampionBuildStats> stats
    ) {
        given(statsRepository.findAllMatchingStatsForScope(
                eq(patch),
                eq(420),
                eq(RECOMMENDATION_TIERS),
                eq(1L),
                eq(position.name()),
                anyBoolean(),
                anyBoolean(),
                anyBoolean(),
                anyBoolean(),
                anyBoolean()
        )).willReturn(stats);
    }

    private RecommendationProperties recommendationProperties(int v2MinSampleCount) {
        return new RecommendationProperties(
                "checkpoint-a-4",
                "checkpoint-c-1-counter",
                "checkpoint-d-1",
                2,
                10,
                5,
                0.8,
                1.0,
                1.0,
                1.0,
                1.0,
                v2MinSampleCount
        );
    }

    private ChampionBuildStats stats(
            ChampionPosition position,
            String buildKey,
            List<Item> items,
            int gameCount
    ) {
        return new ChampionBuildStats(
                "16.15",
                420,
                myChampion,
                position,
                false,
                false,
                false,
                false,
                false,
                "PLATINUM",
                buildKey,
                items,
                gameCount / 2,
                gameCount
        );
    }

    private List<Item> completeItems(int itemCount) {
        return completeItems(itemCount, 1L);
    }

    private List<Item> completeItems(int itemCount, long firstItemId) {
        List<Item> items = new ArrayList<>();
        items.add(armorItem(firstItemId));
        items.add(armorItem(firstItemId + 1));
        items.add(armorItem(firstItemId + 2));
        items.add(new Item(firstItemId + 3, "신발", List.of("Boots")));
        for (int index = 4; index < itemCount; index++) {
            items.add(new Item(firstItemId + index, "후반 아이템 " + index));
        }
        return List.copyOf(items);
    }

    private Item armorItem(long itemId) {
        return new Item(itemId, "방어 아이템 " + itemId, List.of("Armor"));
    }

    private Champion champion(long id, String name, ChampionTag tag) {
        return new Champion(id, name, name, List.of(tag));
    }

    private ChampionBuildPolicy policy(ChampionTag tag) {
        ChampionBuildPolicy policy = mock(ChampionBuildPolicy.class);
        given(policy.supportedTag()).willReturn(tag);
        return policy;
    }

    private List<ChampionBuildPolicy> buildPolicies() {
        return List.of(
                new TankBuildPolicy(),
                new FighterBuildPolicy(),
                new MageBuildPolicy(),
                new AssassinBuildPolicy(),
                new MarksmanBuildPolicy(),
                new SupportBuildPolicy()
        );
    }
}
