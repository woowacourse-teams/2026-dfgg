package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dfgg.application.champion.ChampionService;
import dfgg.application.item.ItemService;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.HardValidityFilter;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.feature.FeatureVector;
import dfgg.application.recommend.v3.ranker.RankedCandidate;
import dfgg.application.recommend.v3.ScoredItem;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.common.exception.InvalidRecommendationRequestException;
import dfgg.common.exception.NextItemRecommendationNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemExclusionGroups;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NextItemRecommendationServiceTest {

    private static final long KRAKEN = 6673L;
    private static final long INFINITY_EDGE = 3031L;
    private static final long LIANDRY = 6653L;
    private static final long MIKAELS_BLESSING = 3222L;

    private ChampionService championService;
    private ItemService itemService;
    private CandidateGenerator buildGenerator;
    private CandidateRanker candidateRanker;
    private ChampionRepository championRepository;
    private NextItemRecommendationService service;

    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(NextItemRecommendationService.class);
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
    private Level previousLogLevel;

    @BeforeEach
    void setUp() {
        championService = mock(ChampionService.class);
        itemService = mock(ItemService.class);
        buildGenerator = mock(CandidateGenerator.class);
        candidateRanker = mock(CandidateRanker.class);
        championRepository = mock(dfgg.domain.champion.ChampionRepository.class);

        when(championService.findChampionByName(any())).thenAnswer(invocation -> {
            Champion champion = mock(Champion.class);
            when(champion.getChampionId()).thenReturn(championIdOf(invocation.getArgument(0)));
            when(champion.getName()).thenReturn(java.util.Map.of("ko-KR", invocation.getArgument(0)));
            return champion;
        });
        when(buildGenerator.source()).thenReturn(CandidateSource.BUILD);
        when(candidateRanker.modelVersion()).thenReturn("test-ranker");

        service = new NextItemRecommendationService(
                championService, itemService, List.of(buildGenerator),
                new HardValidityFilter(new ItemExclusionGroups()), candidateRanker,
                new dfgg.application.recommend.v3.CandidateTopK(20, 20, 20, 30),
                trivialShapCalculator(),
                new dfgg.application.recommend.v3.explanation.ChampionDirectory(championRepository),
                new dfgg.domain.item.trait.ItemTraitCatalog()
        );
    }

    @AfterEach
    void detachLogAppender() {
        serviceLogger.detachAppender(logAppender);
        serviceLogger.setLevel(previousLogLevel);
    }

    /** 이 서비스의 로그를 붙잡는다. 레벨은 테스트가 정한다. */
    private void captureLogsAt(Level level) {
        previousLogLevel = serviceLogger.getLevel();
        serviceLogger.setLevel(level);
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    /** 이유 계산은 여기 관심사가 아니다. 분기 없는 트리라 기여도가 전부 0이 된다. */
    private dfgg.application.recommend.v3.ranker.TreeShapCalculator trivialShapCalculator() {
        var tree = new dfgg.application.recommend.v3.ranker.DecisionTree(
                new int[]{}, new double[]{}, new boolean[]{},
                new int[]{}, new int[]{}, new double[]{0.0},
                new double[]{}, new double[]{1.0});
        return new dfgg.application.recommend.v3.ranker.TreeShapCalculator(
                new dfgg.application.recommend.v3.ranker.GradientBoostedTrees(List.of(tree)),
                dfgg.application.recommend.v3.feature.FeatureName.values().length);
    }

    private long championIdOf(String name) {
        return switch (name) {
            case "야스오", "Yasuo" -> 157L;
            case "징크스" -> 222L;
            case "쓰레쉬" -> 412L;
            case "리신" -> 64L;
            case "오른" -> 516L;
            case "람머스" -> 33L;
            case "아리" -> 103L;
            case "케이틀린" -> 51L;
            case "레오나" -> 89L;
            default -> 60L;
        };
    }

    private NextItemRecommendationRequest request() {
        return new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"), List.of(),
                List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("쓰레쉬", "SUPPORT"),
                        new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                List.of(new ChampionDto("람머스", "TOP"), new ChampionDto("아리", "MID"),
                        new ChampionDto("케이틀린", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                        new ChampionDto("엘리스", "JUNGLE")),
                "EMERALD", "16.17"
        );
    }

    private void givenCandidates(long... itemIds) {
        List<ScoredItem> scored = java.util.stream.LongStream.of(itemIds)
                .mapToObj(id -> new ScoredItem(id, 0.5))
                .toList();
        when(buildGenerator.generate(any(RecommendationQuery.class), anyInt()))
                .thenReturn(GeneratorResult.of(CandidateSource.BUILD, scored));
        when(itemService.findItemsByIds(any())).thenReturn(
                java.util.stream.LongStream.of(itemIds)
                        .mapToObj(id -> new Item(id, Map.of("ko-KR", "아이템" + id), List.of()))
                        .toList()
        );
    }

    /** 랭커는 순위와 함께 feature를 돌려준다. 여기서는 순서만 보므로 빈 벡터로 채운다. */
    private List<RankedCandidate> rankedOf(long... itemIds) {
        return java.util.stream.LongStream.of(itemIds)
                .mapToObj(id -> new RankedCandidate(id, 0.0, FeatureVector.empty()))
                .toList();
    }

    @Test
    @DisplayName("최종 순서는 랭커가 정한 그대로다 — 서비스가 다시 정렬하지 않는다")
    void recommendNextItem_WhenRankerReturnsOrder_PreservesItExactly() {
        // given: 랭커가 generator 점수 순서와 다른 순서를 돌려줘도 그대로 따라야 한다
        givenCandidates(KRAKEN, INFINITY_EDGE, LIANDRY);
        when(candidateRanker.rank(any(CandidateUnion.class), any(RecommendationQuery.class), anyInt()))
                .thenReturn(rankedOf(LIANDRY, KRAKEN, INFINITY_EDGE));

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(request());

        // then
        assertThat(response.recommendedItems()).extracting(item -> item.id())
                .containsExactly(LIANDRY, KRAKEN, INFINITY_EDGE);
    }

    @Test
    @DisplayName("후보가 하나도 없으면 기존과 같이 NotFound를 던진다 — 404 계약을 유지한다")
    void recommendNextItem_WhenNoCandidates_ThrowsNotFound() {
        // given
        when(buildGenerator.generate(any(RecommendationQuery.class), anyInt()))
                .thenReturn(GeneratorResult.of(CandidateSource.BUILD, List.of()));
        when(itemService.findItemsByIds(any())).thenReturn(List.of());
        when(candidateRanker.rank(any(), any(), anyInt())).thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> service.recommendNextItem(request()))
                .isInstanceOf(NextItemRecommendationNotFoundException.class);
    }

    @Test
    @DisplayName("내 챔피언이 아군 목록에도 있으면 입력 오류다 — 서버 내부 오류로 새지 않는다")
    void recommendNextItem_WhenMyChampionIsAlsoAnAlly_ThrowsInvalidRequest() {
        // given
        NextItemRecommendationRequest request = new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"), List.of(),
                List.of(new ChampionDto("야스오", "BOTTOM"), new ChampionDto("쓰레쉬", "SUPPORT"),
                        new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                List.of(new ChampionDto("람머스", "TOP"), new ChampionDto("아리", "MID"),
                        new ChampionDto("케이틀린", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                        new ChampionDto("엘리스", "JUNGLE")),
                "EMERALD", "16.17");

        // when & then
        assertThatThrownBy(() -> service.recommendNextItem(request))
                .isInstanceOf(InvalidRecommendationRequestException.class);
    }

    @Test
    @DisplayName("다른 표기로 들어와도 같은 챔피언이면 입력 오류다 — 이름이 아니라 해석된 ID로 비교한다")
    void recommendNextItem_WhenMyChampionIsAnEnemyUnderAnotherSpelling_ThrowsInvalidRequest() {
        // given
        NextItemRecommendationRequest request = new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"), List.of(),
                List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("쓰레쉬", "SUPPORT"),
                        new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                List.of(new ChampionDto("람머스", "TOP"), new ChampionDto("Yasuo", "MID"),
                        new ChampionDto("케이틀린", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                        new ChampionDto("엘리스", "JUNGLE")),
                "EMERALD", "16.17");

        // when & then
        assertThatThrownBy(() -> service.recommendNextItem(request))
                .isInstanceOf(InvalidRecommendationRequestException.class);
    }

    @Test
    @DisplayName("traits는 팀이 정한 표시명으로 낸다 — enum 이름이 화면에 새지 않는다")
    void recommendNextItem_WhenItemHasTraits_ReturnsDisplayNames() {
        // given
        givenCandidates(MIKAELS_BLESSING);
        when(candidateRanker.rank(any(), any(), anyInt())).thenReturn(rankedOf(MIKAELS_BLESSING));

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(request());

        // then
        assertThat(response.recommendedItems().getFirst().description().traits())
                .containsExactly("CC 해제 및 회복");
    }

    @Test
    @DisplayName("DEBUG가 켜져 있으면 추천마다 순위를 낸 모델, SHAP 묶음 기여도, 후보 소속을 로그로 남긴다")
    void recommendNextItem_WhenDebugEnabled_LogsContributionsWithSources() {
        // given: 부호만으로는 의미가 정해지지 않는다 — 어느 generator가 찾았는지를 함께 남긴다
        captureLogsAt(Level.DEBUG);
        givenCandidates(KRAKEN);
        when(candidateRanker.rank(any(), any(), anyInt())).thenReturn(rankedOf(KRAKEN));

        // when
        service.recommendNextItem(request());

        // then
        assertThat(logAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("model=test-ranker", "itemId=" + KRAKEN, "sources=[BUILD]",
                                "baseValue=", "BUILD="));
    }

    @Test
    @DisplayName("DEBUG가 꺼져 있으면 기여도 로그를 남기지 않는다 — 계산 비용도 들지 않는다")
    void recommendNextItem_WhenDebugDisabled_LogsNoContributions() {
        // given
        captureLogsAt(Level.INFO);
        givenCandidates(KRAKEN);
        when(candidateRanker.rank(any(), any(), anyInt())).thenReturn(rankedOf(KRAKEN));

        // when
        service.recommendNextItem(request());

        // then
        assertThat(logAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("baseValue="));
    }

    @Test
    @DisplayName("아군과 적에 같은 챔피언이 있으면 입력 오류다 — 한 게임에 같은 챔피언은 둘일 수 없다")
    void recommendNextItem_WhenSameChampionOnBothTeams_ThrowsInvalidRequest() {
        // given: 징크스가 아군에도 적에도 있다
        NextItemRecommendationRequest request = new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"), List.of(),
                List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("쓰레쉬", "SUPPORT"),
                        new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                List.of(new ChampionDto("람머스", "TOP"), new ChampionDto("아리", "MID"),
                        new ChampionDto("징크스", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                        new ChampionDto("엘리스", "JUNGLE")),
                "EMERALD", "16.17");

        // when & then
        assertThatThrownBy(() -> service.recommendNextItem(request))
                .isInstanceOf(InvalidRecommendationRequestException.class)
                .hasMessageContaining("징크스");
    }

    @Test
    @DisplayName("아군 안에서 같은 챔피언이 겹쳐도 입력 오류다")
    void recommendNextItem_WhenAllyRepeats_ThrowsInvalidRequest() {
        // given
        NextItemRecommendationRequest request = new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"), List.of(),
                List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("징크스", "SUPPORT"),
                        new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                List.of(new ChampionDto("람머스", "TOP"), new ChampionDto("아리", "MID"),
                        new ChampionDto("케이틀린", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                        new ChampionDto("엘리스", "JUNGLE")),
                "EMERALD", "16.17");

        // when & then
        assertThatThrownBy(() -> service.recommendNextItem(request))
                .isInstanceOf(InvalidRecommendationRequestException.class)
                .hasMessageContaining("징크스");
    }

    @Test
    @DisplayName("구매 아이템이 포지션 상한에 닿아 풀템이면 빈 추천을 낸다")
    void recommendNextItem_WhenFullBuild_ReturnsEmptyRecommendation() {
        // given
        NextItemRecommendationRequest request = midRequestWithPurchased(
                List.of(3031L, 3036L, 3046L, 3072L, 3094L, 6672L));

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(request);

        // then
        assertThat(response.recommendedItems()).isEmpty();
    }

    @Test
    @DisplayName("상한 아래면 평소대로 추천한다 — 풀템 처리가 정상 요청을 막지 않는다")
    void recommendNextItem_WhenBelowPositionLimit_RecommendsAsUsual() {
        // given
        givenCandidates(KRAKEN);
        when(candidateRanker.rank(any(), any(), anyInt())).thenReturn(rankedOf(KRAKEN));
        NextItemRecommendationRequest request = midRequestWithPurchased(
                List.of(3031L, 3036L, 3046L, 3072L, 3094L));

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(request);

        // then
        assertThat(response.recommendedItems()).extracting(item -> item.id()).containsExactly(KRAKEN);
    }

    private NextItemRecommendationRequest midRequestWithPurchased(List<Long> purchasedItemIds) {
        return new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"), purchasedItemIds,
                List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("쓰레쉬", "SUPPORT"),
                        new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                List.of(new ChampionDto("람머스", "TOP"), new ChampionDto("아리", "MID"),
                        new ChampionDto("케이틀린", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                        new ChampionDto("엘리스", "JUNGLE")),
                "EMERALD", "16.17");
    }
}
