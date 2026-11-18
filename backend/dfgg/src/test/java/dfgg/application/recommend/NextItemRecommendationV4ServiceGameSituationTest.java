package dfgg.application.recommend;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.champion.ChampionService;
import dfgg.application.recommend.component.ComponentCandidateFeatures;
import dfgg.application.recommend.component.ComponentPurchaseBundleMapper;
import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.recommend.component.ComponentRecommendationService;
import dfgg.application.recommend.component.ComponentRecommendationService.Status;
import dfgg.application.recommend.component.ComponentRecommendationService.TargetRecommendation;
import dfgg.application.recommend.component.ScoredPurchase;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.application.recommend.situational.SituationalComponentRuntime;
import dfgg.application.recommend.situational.SituationalItemMapper;
import dfgg.application.recommend.v3.MyState;
import dfgg.application.recommend.v3.ReportedGameState;
import dfgg.common.exception.ComponentRecommendationUnavailableException;
import dfgg.domain.champion.Champion;
import dfgg.domain.image.ImageUrls;
import dfgg.domain.item.trait.ItemTraitCatalog;
import dfgg.domain.language.Language;
import dfgg.infrastructure.config.LeagueOfLegendsVersionProperties;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.RecommendationDescription;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.request.NextItemRecommendationV4Request;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import dfgg.presentation.dto.response.NextItemRecommendationV4Response;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 새 추천 API가 타는 길. Core만 상황과 함께 부르고, 조합 아이템과 상황 대응 아이템은 v4의 흐름을 같이 탄다.
 *
 * <p>v4의 지표는 부하 측정 스크립트가 읽는다. 새 길의 호출이 그 지표에 섞이지 않는 것과,
 * 어느 모델이 답했는지가 새 길의 지표에 남는 것을 본다. 기존 길은 {@link NextItemRecommendationV4ServiceTest}가 지킨다.
 */
class NextItemRecommendationV4ServiceGameSituationTest {

    private static final List<String> STAGES = List.of("prepare", "core", "situational", "component");

    private final NextItemRecommendationService core = mock(NextItemRecommendationService.class);
    private final ComponentRecommendationService component = mock(ComponentRecommendationService.class);
    private final ChampionService champions = mock(ChampionService.class);
    private final CompositionFeatureCalculator compositions = mock(CompositionFeatureCalculator.class);
    private final ImageUrls images = new ImageUrls("assets", "ap-northeast-2", "16.18");
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private ItemResponse catalog;
    private NextItemRecommendationV4Service service;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ObjectMapper().readValue("""
                {"version":"16.18","dataVersion":"16.18.1","data":{
                  "1036":{"name":"롱소드","gold":{"total":350,"purchasable":true}},
                  "3133":{"name":"목표 A","from":["1036","1036"],"gold":{"total":1100,"purchasable":true}},
                  "3031":{"name":"무한의 대검","gold":{"total":3450,"purchasable":true}}
                },"englishNames":{}}
                """, ItemResponse.class);
        service = service(ComponentRecommendationRuntime.available(component, catalog));
        for (int id = 1; id <= 9; id++) {
            champion("champion" + id, id);
        }
        champion("Garen", 86);
        when(compositions.calculate(anyList(), anyList())).thenReturn(NO_TAGS);
    }

    private NextItemRecommendationV4Service service(ComponentRecommendationRuntime runtime) {
        return new NextItemRecommendationV4Service(core, runtime, champions, compositions,
                new ComponentPurchaseBundleMapper(images), new LeagueOfLegendsVersionProperties("16.18"),
                SituationalComponentRuntime.disabled(), new SituationalItemMapper(images, new ItemTraitCatalog()),
                meterRegistry);
    }

    private void champion(String name, long id) {
        Champion champion = mock(Champion.class);
        when(champion.getChampionId()).thenReturn(id);
        when(champions.findChampionByName(name)).thenReturn(champion);
    }

    private NextItemRecommendationV4Request request() {
        List<ChampionDto> allies = IntStream.rangeClosed(1, 4)
                .mapToObj(index -> new ChampionDto("champion" + index, "TOP")).toList();
        List<ChampionDto> enemies = IntStream.rangeClosed(5, 9)
                .mapToObj(index -> new ChampionDto("champion" + index, "TOP")).toList();
        return new NextItemRecommendationV4Request(new ChampionDto("Garen", "TOP"), List.of(3031L), allies,
                enemies, "EMERALD", null, List.of(), 1000L, 600_000L);
    }

    private ReportedGameState state() {
        ReportedGameState.Player player = new ReportedGameState.Player(1, 0, 0, List.of());
        return new ReportedGameState(new MyState(1, 0, 0, 0),
                Collections.nCopies(4, player), Collections.nCopies(5, player));
    }

    private RecommendedItemDto coreItem(long id) {
        return new RecommendedItemDto(id, "core" + id, "core-image" + id,
                new RecommendationDescription(List.of(), List.of(), List.of("trait")));
    }

    private void givenCoreWithState(List<RecommendedItemDto> items) {
        when(core.recommendNextItem(any(), any(ReportedGameState.class), any()))
                .thenReturn(new NextItemRecommendationResponse(items));
    }

    private void givenComponent(TargetRecommendation... recommendations) {
        when(component.recommend(any(), eq(catalog))).thenReturn(List.of(recommendations));
    }

    private ScoredPurchase purchase(int id, int quantity) {
        List<Double> features = Collections.nCopies(ComponentCandidateFeatures.FEATURE_NAMES.size(), 0.0);
        return new ScoredPurchase(new ComponentCandidateFeatures(Map.of(id, quantity),
                Collections.nCopies(quantity, id), Map.of(), features), 350L * quantity, 1, 1);
    }

    private Timer v5Stage(String stage, String model) {
        return meterRegistry.get(NextItemRecommendationV4Service.V5_STAGE_METRIC)
                .tag("stage", stage).tag(NextItemRecommendationV4Service.MODEL_TAG, model).timer();
    }

    private List<String> meterNames() {
        return meterRegistry.getMeters().stream().map(meter -> meter.getId().getName()).distinct().toList();
    }

    @Test
    @DisplayName("상태와 함께 부르면 Core를 v3 서비스의 상황 길로 부르고, 응답은 v4와 같은 모양이다")
    void recommendNextItem_WhenStateGiven_CallsCoreWithStateAndKeepsV4ResponseShape() {
        // given
        ReportedGameState state = state();
        NextItemRecommendationV4Request request = request();
        RecommendedItemDto first = coreItem(3133L);
        givenCoreWithState(List.of(first));
        givenComponent(new TargetRecommendation(3133, Status.OK, List.of(purchase(1036, 2)), null));

        // when
        NextItemRecommendationV4Response response = service.recommendNextItem(request, state, Language.KO_KR);

        // then: Core 요청은 v4와 같은 모양이고(패치는 운영 패치), 상태는 받은 그대로 넘긴다
        verify(core).recommendNextItem(
                new NextItemRecommendationRequest(request.myChampion(), List.of(3031L),
                        request.allies(), request.enemies(), "EMERALD", "16.18"),
                state, Language.KO_KR);
        verify(core, never()).recommendNextItem(any(), any());
        assertThat(response.recommendedItems()).extracting(item -> item.id()).containsExactly(3133L);
        assertThat(response.recommendedItems().getFirst().description()).isSameAs(first.description());
        assertThat(response.recommendedItems().getFirst().purchaseBundle().items().getFirst().itemId())
                .isEqualTo(1036L);
        assertThat(response.situationalItems()).isEmpty();
    }

    @Test
    @DisplayName("상태와 함께 불러도 v4의 지표는 늘지 않는다 — 부하 측정이 읽는 수치에 새 API가 섞이지 않는다")
    void recommendNextItem_WhenStateGiven_DoesNotTouchV4Meters() {
        // given
        givenCoreWithState(List.of(coreItem(3133L)));
        givenComponent(new TargetRecommendation(3133, Status.NO_CANDIDATE, List.of(), null));

        // when
        service.recommendNextItem(request(), state(), Language.KO_KR);

        // then
        assertThat(meterNames()).containsExactly(NextItemRecommendationV4Service.V5_STAGE_METRIC);
    }

    @Test
    @DisplayName("새 모델이 답하면 새 API의 단계별 시간에 새 모델 꼬리표가 붙는다")
    void recommendNextItem_WhenGameSituationModelRanks_TagsV5MetersWithIt() {
        // given
        when(core.ranksWithGameSituationModel()).thenReturn(true);
        givenCoreWithState(List.of(coreItem(3133L)));
        givenComponent(new TargetRecommendation(3133, Status.NO_CANDIDATE, List.of(), null));

        // when
        service.recommendNextItem(request(), state(), Language.KO_KR);

        // then
        String model = NextItemRecommendationV4Service.GAME_SITUATION_MODEL;
        for (String stage : STAGES) {
            assertThat(v5Stage(stage, model).count()).as(stage).isEqualTo(1);
        }
        assertThat(v5Stage("total", model).count()).isEqualTo(1);
        assertThat(meterRegistry.find(NextItemRecommendationV4Service.V5_STAGE_METRIC)
                .tag(NextItemRecommendationV4Service.MODEL_TAG, NextItemRecommendationV4Service.BASELINE_MODEL)
                .timer()).isNull();
    }

    @Test
    @DisplayName("새 모델이 없어 지금 모델이 답하면 지금 모델 꼬리표가 붙는다 — 응답으로는 구분되지 않아 여기서 드러낸다")
    void recommendNextItem_WhenBaselineModelRanks_TagsV5MetersWithIt() {
        // given
        when(core.ranksWithGameSituationModel()).thenReturn(false);
        givenCoreWithState(List.of(coreItem(3133L)));
        givenComponent(new TargetRecommendation(3133, Status.NO_CANDIDATE, List.of(), null));

        // when
        service.recommendNextItem(request(), state(), Language.KO_KR);

        // then
        assertThat(v5Stage("total", NextItemRecommendationV4Service.BASELINE_MODEL).count()).isEqualTo(1);
        assertThat(meterRegistry.find(NextItemRecommendationV4Service.V5_STAGE_METRIC)
                .tag(NextItemRecommendationV4Service.MODEL_TAG, NextItemRecommendationV4Service.GAME_SITUATION_MODEL)
                .timer()).isNull();
    }

    @Test
    @DisplayName("상태와 함께 불렀을 때 Core가 비면 새 API의 빈 Core 횟수만 센다")
    void recommendNextItem_WhenStateGivenAndCoreEmpty_CountsV5EmptyCoreOnly() {
        // given
        givenCoreWithState(List.of());

        // when
        NextItemRecommendationV4Response response = service.recommendNextItem(request(), state(), Language.KO_KR);

        // then
        assertThat(response.recommendedItems()).isEmpty();
        assertThat(meterRegistry.get(NextItemRecommendationV4Service.V5_EMPTY_CORE_METRIC)
                .tag(NextItemRecommendationV4Service.MODEL_TAG, NextItemRecommendationV4Service.BASELINE_MODEL)
                .counter().count()).isEqualTo(1);
        assertThat(meterRegistry.find(NextItemRecommendationV4Service.EMPTY_CORE_METRIC).counter()).isNull();
        assertThat(meterRegistry.find(NextItemRecommendationV4Service.STAGE_METRIC).timer()).isNull();
    }

    @Test
    @DisplayName("조합 추천을 쓸 수 없으면 상태와 함께 불러도 v4와 같은 예외를 던진다")
    void recommendNextItem_WhenComponentUnavailable_ThrowsLikeV4() {
        // given
        NextItemRecommendationV4Service unavailable = service(ComponentRecommendationRuntime.unavailable());

        // when & then
        assertThatThrownBy(() -> unavailable.recommendNextItem(request(), state(), Language.KO_KR))
                .isInstanceOf(ComponentRecommendationUnavailableException.class);
    }

    @Test
    @DisplayName("상태 없이 부르는 기존 길은 v4 지표의 이름과 꼬리표가 그대로이고, 새 API의 지표를 만들지 않는다")
    void recommendNextItem_WhenCalledWithoutState_KeepsV4MeterNamesAndTags() {
        // given
        when(core.recommendNextItem(any(), any()))
                .thenReturn(new NextItemRecommendationResponse(List.of(coreItem(3133L))));
        givenComponent(new TargetRecommendation(3133, Status.NO_CANDIDATE, List.of(), null));

        // when
        service.recommendNextItem(request(), Language.KO_KR);

        // then: 꼬리표는 stage 하나뿐이다. 스크립트가 이 이름과 꼬리표로 읽는다
        assertThat(meterNames()).containsExactly(NextItemRecommendationV4Service.STAGE_METRIC);
        assertThat(meterRegistry.getMeters()).extracting(Meter::getId)
                .allSatisfy(id -> assertThat(id.getTags()).extracting(Tag::getKey).containsExactly("stage"));
        verify(core, never()).recommendNextItem(any(), any(ReportedGameState.class), any());
    }
}
