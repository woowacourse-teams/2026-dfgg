package dfgg.application.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dfgg.application.champion.ChampionService;
import dfgg.application.item.ItemService;
import dfgg.application.recommend.v3.CandidateGenerator;
import dfgg.application.recommend.v3.CandidateSource;
import dfgg.application.recommend.v3.CandidateTopK;
import dfgg.application.recommend.v3.CandidateUnion;
import dfgg.application.recommend.v3.GameSituation;
import dfgg.application.recommend.v3.GeneratorResult;
import dfgg.application.recommend.v3.HardValidityFilter;
import dfgg.application.recommend.v3.MyState;
import dfgg.application.recommend.v3.PlayerState;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.ReportedGameState;
import dfgg.application.recommend.v3.ScoredItem;
import dfgg.application.recommend.v3.explanation.ChampionDirectory;
import dfgg.application.recommend.v3.feature.FeatureName;
import dfgg.application.recommend.v3.feature.FeatureVector;
import dfgg.application.recommend.v3.ranker.CandidateRanker;
import dfgg.application.recommend.v3.ranker.CoreRanking;
import dfgg.application.recommend.v3.ranker.DecisionTree;
import dfgg.application.recommend.v3.ranker.GameSituationRanking;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.application.recommend.v3.ranker.RankedCandidate;
import dfgg.application.recommend.v3.ranker.TreeShapCalculator;
import dfgg.common.exception.NextItemRecommendationNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.champion.ChampionRepository;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.item.Item;
import dfgg.domain.item.ItemExclusionGroups;
import dfgg.domain.item.trait.ItemTraitCatalog;
import dfgg.domain.language.Language;
import dfgg.infrastructure.config.LeagueOfLegendsVersionProperties;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

/**
 * 새 추천 API가 타는 길. 요청이 보낸 10명의 상태를 상황에 싣고, 새 모델이 있으면 새 모델로 순위를 매긴다.
 *
 * <p>기존 길(상태 없이 부르는 v3·v4)은 {@link NextItemRecommendationServiceTest}가 지킨다. 여기서는 새 길과,
 * 새 모델이 있어도 기존 길이 바뀌지 않는다는 것을 본다.
 */
class NextItemRecommendationServiceGameSituationTest {

    private static final long KRAKEN = 6673L;
    private static final long INFINITY_EDGE = 3031L;
    private static final long RABADON = 3089L;
    private static final long TRINKET = 3340L;

    /** 두 모델의 기여도 계산기가 로그에서 구분되도록 기준값을 다르게 둔다. */
    private static final double BASELINE_BASE_VALUE = 0.0;
    private static final double GAME_SITUATION_BASE_VALUE = 7.0;

    private ChampionService championService;
    private ItemService itemService;
    private CandidateGenerator buildGenerator;
    private CandidateRanker baselineRanker;
    private CandidateRanker gameSituationRanker;

    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(NextItemRecommendationService.class);
    private final ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
    private Level previousLogLevel;

    @BeforeEach
    void setUp() {
        championService = mock(ChampionService.class);
        itemService = mock(ItemService.class);
        buildGenerator = mock(CandidateGenerator.class);
        baselineRanker = mock(CandidateRanker.class);
        gameSituationRanker = mock(CandidateRanker.class);
        when(itemService.findCoreItemIds()).thenReturn(Set.of(6673, 3031, 3089));
        when(championService.findChampionByName(any())).thenAnswer(invocation -> {
            Champion champion = mock(Champion.class);
            when(champion.getChampionId()).thenReturn(championIdOf(invocation.getArgument(0)));
            when(champion.getName()).thenReturn(Map.of("ko-KR", invocation.getArgument(0)));
            return champion;
        });
        when(buildGenerator.source()).thenReturn(CandidateSource.BUILD);
        when(baselineRanker.modelVersion()).thenReturn("baseline-ranker");
        when(gameSituationRanker.modelVersion()).thenReturn("game-situation-ranker");

        previousLogLevel = serviceLogger.getLevel();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        serviceLogger.detachAppender(logAppender);
        serviceLogger.setLevel(previousLogLevel);
    }

    private NextItemRecommendationService serviceWith(GameSituationRanking gameSituation) {
        return new NextItemRecommendationService(
                championService, itemService, List.of(buildGenerator),
                new HardValidityFilter(new ItemExclusionGroups()), baselineRanker,
                new CandidateTopK(20, 20, 20, 30),
                shapCalculatorOf(BASELINE_BASE_VALUE),
                new ChampionDirectory(mock(ChampionRepository.class)),
                new ItemTraitCatalog(),
                new ImageUrls("test-bucket", "ap-northeast-2", "99.1"),
                new LeagueOfLegendsVersionProperties("99.1"),
                gameSituation
        );
    }

    private NextItemRecommendationService serviceWithGameSituationModel() {
        return serviceWith(GameSituationRanking.available(
                new CoreRanking(gameSituationRanker, shapCalculatorOf(GAME_SITUATION_BASE_VALUE))));
    }

    private NextItemRecommendationService serviceWithoutGameSituationModel() {
        return serviceWith(GameSituationRanking.absent());
    }

    /** 분기 없는 트리라 기여도는 전부 0이고, 기준값이 잎 값이 된다. 어느 모델의 계산기가 쓰였는지를 기준값으로 가린다. */
    private TreeShapCalculator shapCalculatorOf(double baseValue) {
        DecisionTree tree = new DecisionTree(
                new int[]{}, new double[]{}, new boolean[]{},
                new int[]{}, new int[]{}, new double[]{baseValue},
                new double[]{}, new double[]{1.0});
        return new TreeShapCalculator(new GradientBoostedTrees(List.of(tree)), FeatureName.values().length);
    }

    private long championIdOf(String name) {
        return switch (name) {
            case "야스오" -> 157L;
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

    private NextItemRecommendationRequest request(List<Long> purchasedItemIds) {
        return new NextItemRecommendationRequest(
                new ChampionDto("야스오", "MID"), purchasedItemIds,
                List.of(new ChampionDto("징크스", "BOTTOM"), new ChampionDto("쓰레쉬", "SUPPORT"),
                        new ChampionDto("리신", "JUNGLE"), new ChampionDto("오른", "TOP")),
                List.of(new ChampionDto("람머스", "TOP"), new ChampionDto("아리", "MID"),
                        new ChampionDto("케이틀린", "BOTTOM"), new ChampionDto("레오나", "SUPPORT"),
                        new ChampionDto("엘리스", "JUNGLE")),
                "EMERALD", "16.17"
        );
    }

    private NextItemRecommendationRequest request() {
        return request(List.of());
    }

    /** 선수마다 값이 모두 다르다. 짝이 한 칸이라도 밀리면 드러난다. */
    private ReportedGameState reportedState() {
        return new ReportedGameState(
                new MyState(11, 4, 2, 3),
                List.of(player(8, 0, 7, List.of(3190L)), player(7, 1, 9),
                        player(9, 2, 5, List.of(INFINITY_EDGE)), player(10, 3, 1)),
                List.of(player(12, 5, 0, List.of(3075L)), player(13, 6, 2, List.of(RABADON)),
                        player(6, 7, 4), player(5, 8, 6, List.of(3190L, 3109L)), player(4, 9, 8)));
    }

    private ReportedGameState.Player player(int level, int kills, int assists) {
        return player(level, kills, assists, List.of());
    }

    private ReportedGameState.Player player(int level, int kills, int assists, List<Long> itemIds) {
        return new ReportedGameState.Player(level, kills, assists, itemIds);
    }

    private void givenCandidates(long... itemIds) {
        List<ScoredItem> scored = LongStream.of(itemIds).mapToObj(id -> new ScoredItem(id, 0.5)).toList();
        when(buildGenerator.generate(any(RecommendationQuery.class), anyInt()))
                .thenReturn(GeneratorResult.of(CandidateSource.BUILD, scored));
        when(itemService.findItemsByIds(any())).thenReturn(LongStream.of(itemIds)
                .mapToObj(id -> new Item(id, Map.of("ko-KR", "아이템" + id), List.of()))
                .toList());
    }

    private void givenRanking(CandidateRanker ranker, long... itemIds) {
        when(ranker.rank(any(CandidateUnion.class), any(RecommendationQuery.class), anyInt()))
                .thenReturn(LongStream.of(itemIds)
                        .mapToObj(id -> new RankedCandidate(id, 0.0, FeatureVector.empty()))
                        .toList());
    }

    private RecommendationQuery queryGivenTo(CandidateRanker ranker) {
        ArgumentCaptor<RecommendationQuery> query = ArgumentCaptor.forClass(RecommendationQuery.class);
        verify(ranker).rank(any(CandidateUnion.class), query.capture(), anyInt());
        return query.getValue();
    }

    private List<Long> itemIdsOf(NextItemRecommendationResponse response) {
        return response.recommendedItems().stream().map(RecommendedItemDto::id).toList();
    }

    @Test
    @DisplayName("요청이 보낸 10명의 상태가 선수 순서대로 챔피언 번호·포지션과 짝지어져 상황에 실린다")
    void recommendNextItem_WhenStateReported_CarriesItOnQuerySituation() {
        // given
        givenCandidates(KRAKEN);
        givenRanking(gameSituationRanker, KRAKEN);

        // when
        serviceWithGameSituationModel().recommendNextItem(request(), reportedState(), Language.KO_KR);

        // then
        GameSituation.Known situation = (GameSituation.Known) queryGivenTo(gameSituationRanker).situation();
        assertThat(situation.me()).isEqualTo(new MyState(11, 4, 2, 3));
        assertThat(situation.allies()).containsExactly(
                new PlayerState(222L, ChampionPosition.BOTTOM, 8, 0, 7, List.of(3190L)),
                new PlayerState(412L, ChampionPosition.SUPPORT, 7, 1, 9, List.of()),
                new PlayerState(64L, ChampionPosition.JUNGLE, 9, 2, 5, List.of(INFINITY_EDGE)),
                new PlayerState(516L, ChampionPosition.TOP, 10, 3, 1, List.of()));
        assertThat(situation.enemies()).containsExactly(
                new PlayerState(33L, ChampionPosition.TOP, 12, 5, 0, List.of(3075L)),
                new PlayerState(103L, ChampionPosition.MID, 13, 6, 2, List.of(RABADON)),
                new PlayerState(51L, ChampionPosition.BOTTOM, 6, 7, 4, List.of()),
                new PlayerState(89L, ChampionPosition.SUPPORT, 5, 8, 6, List.of(3190L, 3109L)),
                new PlayerState(60L, ChampionPosition.JUNGLE, 4, 9, 8, List.of()));
    }

    @Test
    @DisplayName("한 선수의 아이템에 같은 번호가 두 번 오면 한 번만 싣고, Core가 아닌 번호는 그대로 싣는다")
    void recommendNextItem_WhenPlayerHoldsSameItemTwice_CarriesItOnce() {
        // given: 상대 첫 선수가 라바돈을 두 칸에 들고 장신구도 들고 있다
        ReportedGameState state = new ReportedGameState(
                new MyState(1, 0, 0, 0),
                List.of(player(1, 0, 0), player(1, 0, 0), player(1, 0, 0), player(1, 0, 0)),
                List.of(player(1, 0, 0, List.of(RABADON, TRINKET, RABADON)),
                        player(1, 0, 0), player(1, 0, 0), player(1, 0, 0), player(1, 0, 0)));
        givenCandidates(KRAKEN);
        givenRanking(gameSituationRanker, KRAKEN);

        // when
        serviceWithGameSituationModel().recommendNextItem(request(), state, Language.KO_KR);

        // then: 학습 데이터의 보유 목록도 같은 Core를 한 번만 든다. Core를 추리는 일은 feature를 뽑는 쪽이 한다
        GameSituation.Known situation = (GameSituation.Known) queryGivenTo(gameSituationRanker).situation();
        assertThat(situation.enemies().get(0).coreItemIds()).containsExactly(RABADON, TRINKET);
    }

    @Test
    @DisplayName("새 모델이 있으면 새 모델의 랭커가 순위를 매기고, 디버그 로그의 모델과 기여도도 새 모델 것이다")
    void recommendNextItem_WhenGameSituationModelAvailable_RanksWithIt() {
        // given
        serviceLogger.setLevel(Level.DEBUG);
        givenCandidates(KRAKEN, INFINITY_EDGE);
        givenRanking(gameSituationRanker, INFINITY_EDGE, KRAKEN);
        NextItemRecommendationService service = serviceWithGameSituationModel();

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(request(), reportedState(), Language.KO_KR);

        // then
        assertThat(service.ranksWithGameSituationModel()).isTrue();
        assertThat(itemIdsOf(response)).containsExactly(INFINITY_EDGE, KRAKEN);
        verify(baselineRanker, never()).rank(any(), any(), anyInt());
        assertThat(logAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                .isNotEmpty()
                .allMatch(message -> message.contains("model=game-situation-ranker"))
                .allMatch(message -> message.contains("baseValue=" + GAME_SITUATION_BASE_VALUE));
    }

    @Test
    @DisplayName("새 모델이 없으면 지금 모델의 랭커가 순위를 매기고, 디버그 로그의 모델과 기여도도 지금 모델 것이다")
    void recommendNextItem_WhenGameSituationModelAbsent_RanksWithBaseline() {
        // given
        serviceLogger.setLevel(Level.DEBUG);
        givenCandidates(KRAKEN, INFINITY_EDGE);
        givenRanking(baselineRanker, INFINITY_EDGE, KRAKEN);
        NextItemRecommendationService service = serviceWithoutGameSituationModel();

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(request(), reportedState(), Language.KO_KR);

        // then: 상태는 지금 모델로 답할 때도 상황에 실린다. 지금 모델이 읽지 않을 뿐이다
        assertThat(service.ranksWithGameSituationModel()).isFalse();
        assertThat(itemIdsOf(response)).containsExactly(INFINITY_EDGE, KRAKEN);
        assertThat(queryGivenTo(baselineRanker).situation().isUnknown()).isFalse();
        assertThat(logAppender.list).extracting(ILoggingEvent::getFormattedMessage)
                .isNotEmpty()
                .allMatch(message -> message.contains("model=baseline-ranker"))
                .allMatch(message -> message.contains("baseValue=" + BASELINE_BASE_VALUE));
    }

    @Test
    @DisplayName("기존 생성자로 만든 서비스는 새 모델이 없는 것으로 동작한다")
    void recommendNextItem_WhenBuiltWithExistingConstructor_RanksWithBaseline() {
        // given
        NextItemRecommendationService service = new NextItemRecommendationService(
                championService, itemService, List.of(buildGenerator),
                new HardValidityFilter(new ItemExclusionGroups()), baselineRanker,
                new CandidateTopK(20, 20, 20, 30), shapCalculatorOf(BASELINE_BASE_VALUE),
                new ChampionDirectory(mock(ChampionRepository.class)), new ItemTraitCatalog(),
                new ImageUrls("test-bucket", "ap-northeast-2", "99.1"),
                new LeagueOfLegendsVersionProperties("99.1"));
        givenCandidates(KRAKEN);
        givenRanking(baselineRanker, KRAKEN);

        // when
        NextItemRecommendationResponse response = service.recommendNextItem(request(), reportedState(), Language.KO_KR);

        // then
        assertThat(service.ranksWithGameSituationModel()).isFalse();
        assertThat(itemIdsOf(response)).containsExactly(KRAKEN);
    }

    @Test
    @DisplayName("새 모델이 있어도 상태 없이 부르는 기존 길은 지금 모델로 순위를 매기고 상황은 모름이다")
    void recommendNextItem_WhenCalledWithoutState_StaysOnBaselineEvenIfModelAvailable() {
        // given
        givenCandidates(KRAKEN);
        givenRanking(baselineRanker, KRAKEN);

        // when
        serviceWithGameSituationModel().recommendNextItem(request(), Language.KO_KR);

        // then
        assertThat(queryGivenTo(baselineRanker).situation().isUnknown()).isTrue();
        verify(gameSituationRanker, never()).rank(any(), any(), anyInt());
    }

    @Test
    @DisplayName("풀빌드면 기존 길처럼 빈 추천을 돌려주고 순위를 매기지 않는다")
    void recommendNextItem_WhenFullBuild_ReturnsEmptyLikeExistingPath() {
        // given: MID는 Core 6개가 풀빌드다
        NextItemRecommendationRequest fullBuild = request(List.of(1L, 2L, 3L, 4L, 5L, 6L));

        // when
        NextItemRecommendationResponse response = serviceWithGameSituationModel()
                .recommendNextItem(fullBuild, reportedState(), Language.KO_KR);

        // then
        assertThat(response.recommendedItems()).isEmpty();
        verify(gameSituationRanker, never()).rank(any(), any(), anyInt());
    }

    @Test
    @DisplayName("추천할 것이 없으면 기존 길처럼 NotFound를 던진다")
    void recommendNextItem_WhenNothingRanked_ThrowsNotFoundLikeExistingPath() {
        // given
        givenCandidates(KRAKEN);
        givenRanking(gameSituationRanker);

        // when & then
        assertThatThrownBy(() -> serviceWithGameSituationModel()
                .recommendNextItem(request(), reportedState(), Language.KO_KR))
                .isInstanceOf(NextItemRecommendationNotFoundException.class);
    }

    @Test
    @DisplayName("상태의 선수 수가 요청의 선수 수와 다르면 짝을 지을 수 없어 거부한다")
    void recommendNextItem_WhenStateCountDiffersFromRequest_Throws() {
        // given: 아군은 4명인데 상태는 3명치다
        ReportedGameState threeAllies = new ReportedGameState(
                new MyState(1, 0, 0, 0),
                List.of(player(1, 0, 0), player(1, 0, 0), player(1, 0, 0)),
                List.of(player(1, 0, 0), player(1, 0, 0), player(1, 0, 0), player(1, 0, 0), player(1, 0, 0)));

        // when & then
        assertThatThrownBy(() -> serviceWithGameSituationModel()
                .recommendNextItem(request(), threeAllies, Language.KO_KR))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
