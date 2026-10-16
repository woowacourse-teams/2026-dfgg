package dfgg.application.recommend;

import static dfgg.application.recommend.context.TestCompositions.NO_TAGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dfgg.domain.language.Language;
import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.champion.ChampionService;
import dfgg.application.recommend.component.ComponentCandidateFeatures;
import dfgg.application.recommend.component.ComponentPurchaseBundleMapper;
import dfgg.application.recommend.component.ComponentRecommendationQuery;
import dfgg.application.recommend.component.ComponentRecommendationRuntime;
import dfgg.application.recommend.component.ComponentRecommendationService;
import dfgg.application.recommend.component.ComponentRecommendationService.Status;
import dfgg.application.recommend.component.ComponentRecommendationService.TargetRecommendation;
import dfgg.application.recommend.component.ScoredPurchase;
import dfgg.application.recommend.context.CompositionFeatureCalculator;
import dfgg.application.recommend.v3.ranker.DecisionTree;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.common.exception.ComponentRecommendationUnavailableException;
import dfgg.common.exception.InvalidRecommendationRequestException;
import dfgg.common.exception.NextItemRecommendationNotFoundException;
import dfgg.domain.champion.Champion;
import dfgg.domain.image.ImageUrls;
import dfgg.infrastructure.config.LeagueOfLegendsVersionProperties;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.ChampionDto;
import dfgg.presentation.dto.ChampionRefDto;
import dfgg.presentation.dto.RecommendationDescription;
import dfgg.presentation.dto.RecommendedItemDto;
import dfgg.presentation.dto.request.InventoryItemRequest;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.request.NextItemRecommendationV4Request;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class NextItemRecommendationV4ServiceTest {
    private final NextItemRecommendationService core = mock(NextItemRecommendationService.class);
    private final ComponentRecommendationService component = mock(ComponentRecommendationService.class);
    private final ChampionService champions = mock(ChampionService.class);
    private final CompositionFeatureCalculator compositions = mock(CompositionFeatureCalculator.class);
    private final ImageUrls images = new ImageUrls("assets", "ap-northeast-2", "16.18");
    private ItemResponse catalog;
    private NextItemRecommendationV4Service service;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new ObjectMapper().readValue("""
                {"version":"16.18","dataVersion":"16.18.1","data":{
                  "1036":{"name":"롱소드","gold":{"total":350,"purchasable":true}},
                  "1052":{"name":"증폭의 고서","gold":{"total":400,"purchasable":true}},
                  "1001":{"name":"장화","tags":["Boots"],"gold":{"total":300,"purchasable":true}},
                  "3133":{"name":"목표 A","from":["1036","1036"],"gold":{"total":1100,"purchasable":true}},
                  "3071":{"name":"목표 B","from":["1036","1036"],"gold":{"total":1100,"purchasable":true}},
                  "2055":{"name":"제어 와드","stacks":2,"gold":{"total":75,"purchasable":true}},
                  "3123":{"name":"처형인의 대검","gold":{"total":800,"purchasable":true}},
                  "3076":{"name":"덤불 조끼","gold":{"total":800,"purchasable":true}}
                },"englishNames":{"3123":"Executioner's Calling","3076":"Bramble Vest"}}
                """, ItemResponse.class);
        service = service(ComponentRecommendationRuntime.available(component, catalog));
    }

    @Test
    void Core_순서와_설명을_보존하고_목표별_최상위_구매_하나만_붙인다() {
        var request = request(null, List.of(new InventoryItemRequest(1036L, 2),
                new InventoryItemRequest(1001L, 1), new InventoryItemRequest(2055L, 2)));
        var first = coreItem(3133L);
        var second = coreItem(3086L);
        var third = coreItem(3071L);
        prepareCore(List.of(first, second, third));
        when(component.recommend(any(), eq(catalog))).thenReturn(List.of(
                new TargetRecommendation(3071, Status.OK, List.of(purchase(1052, 1)), null),
                new TargetRecommendation(3086, Status.NO_CANDIDATE, List.of(), "gold"),
                new TargetRecommendation(3133, Status.OK, List.of(purchase(1036, 2), purchase(1052, 1)), null)));

        var response = service.recommendNextItem(request, Language.KO_KR);

        assertThat(response.recommendedItems()).extracting(item -> item.id()).containsExactly(3133L, 3086L, 3071L);
        var recommended = response.recommendedItems().getFirst();
        assertThat(recommended.name()).isEqualTo(first.name());
        assertThat(recommended.imageUrl()).isEqualTo(first.imageUrl());
        assertThat(recommended.description()).isSameAs(first.description());
        assertThat(recommended.purchaseBundle().items()).hasSize(1);
        var item = recommended.purchaseBundle().items().getFirst();
        assertThat(item.itemId()).isEqualTo(1036L);
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.name()).isEqualTo("롱소드");
        assertThat(item.imageUrl()).isEqualTo(images.itemOf(1036));
        assertThat(recommended.purchaseBundle().purchaseOrder()).containsExactly(1036L, 1036L);
        assertThat(recommended.purchaseBundle().additionalCost()).isEqualTo(700);
        assertThat(response.recommendedItems().get(1).purchaseBundle()).isNull();
        assertThat(response.recommendedItems().get(2).purchaseBundle().items().getFirst().itemId()).isEqualTo(1052L);

        var captured = ArgumentCaptor.forClass(ComponentRecommendationQuery.class);
        verify(component).recommend(captured.capture(), eq(catalog));
        var query = captured.getValue();
        assertThat(query.targetItemIds()).containsExactly(3133, 3086, 3071);
        assertThat(query.inventory().quantities()).isEqualTo(Map.of(1036, 2, 1001, 1, 2055, 2));
        assertThat(query.inventory().bootsInQuestSlot()).isFalse();
        assertThat(query.inventory().controlWardsInQuestSlot()).isFalse();
        assertThat(query.availableGold()).isEqualTo(1000);
        assertThat(query.gameTimeMs()).isEqualTo(600_000);
        assertThat(query.championId()).isEqualTo(86);
        assertThat(query.patch()).isEqualTo("16.18");
        assertThat(query.composition()).isSameAs(NO_TAGS);
        verify(core).recommendNextItem(new NextItemRecommendationRequest(request.myChampion(), List.of(3031L),
                request.allies(), request.enemies(), "EMERALD", "16.18"), Language.KO_KR);
        verify(compositions).calculate(List.of(1L, 2L, 3L, 4L), List.of(5L, 6L, 7L, 8L, 9L));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "16.18"})
    void 패치가_비었으면_운영_패치를_Core와_Component에_동일하게_전달한다(String patch) {
        prepareCore(List.of(coreItem(3133)));
        when(component.recommend(any(), eq(catalog))).thenReturn(List.of(
                new TargetRecommendation(3133, Status.NO_CANDIDATE, List.of(), null)));
        service.recommendNextItem(request(patch, List.of()), Language.KO_KR);
        var captured = ArgumentCaptor.forClass(ComponentRecommendationQuery.class);
        verify(component).recommend(captured.capture(), eq(catalog));
        assertThat(captured.getValue().patch()).isEqualTo("16.18");
    }

    @Test
    void 실제_Component_계산에서도_각_목표가_같은_재료와_예산을_독립적으로_사용한다() {
        prepareCore(List.of(coreItem(3133), coreItem(3071)));
        var model = new GradientBoostedTrees(List.of(new DecisionTree(new int[]{5}, new double[]{0.5},
                new boolean[]{true}, new int[]{-1}, new int[]{-2}, new double[]{0, 10},
                new double[]{2}, new double[]{1, 1})));
        var connected = service(ComponentRecommendationRuntime.available(new ComponentRecommendationService(model), catalog));

        var response = connected.recommendNextItem(request(null, List.of(new InventoryItemRequest(1036L, 1))), Language.KO_KR);

        assertThat(response.recommendedItems()).hasSize(2).allSatisfy(item -> {
            assertThat(item.purchaseBundle()).isNotNull();
            assertThat(item.purchaseBundle().additionalCost()).isEqualTo(750);
            assertThat(item.purchaseBundle().purchaseOrder()).containsExactly(item.id());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"16.17", "16.18.1", "invalid"})
    void 운영_패치와_다르면_추천을_호출하지_않는다(String patch) {
        assertThatThrownBy(() -> service.recommendNextItem(request(patch, List.of()), Language.KO_KR))
                .isInstanceOf(InvalidRecommendationRequestException.class);
        verifyNoInteractions(core, component, champions, compositions);
    }

    @Test
    void 준비되지_않은_Component는_상세_원인_없는_서비스_불가_예외를_반환한다() {
        var unavailable = service(ComponentRecommendationRuntime.unavailable());
        assertThatThrownBy(() -> unavailable.recommendNextItem(request(null, List.of()), Language.KO_KR))
                .isInstanceOf(ComponentRecommendationUnavailableException.class)
                .hasMessage("현재 Component 추천을 사용할 수 없습니다.");
        verifyNoInteractions(core, component, champions, compositions);
    }

    @Test
    void 모르는_보유_아이템은_입력_오류로_거부한다() {
        assertThatThrownBy(() -> service.recommendNextItem(request(null, List.of(new InventoryItemRequest(999L, 1))), Language.KO_KR))
                .isInstanceOf(InvalidRecommendationRequestException.class).hasMessageContaining("999");
        verifyNoInteractions(core, component, champions, compositions);
    }

    @Test
    void Core가_빈_결과이면_Component를_계산하지_않는다() {
        when(core.recommendNextItem(any(), any())).thenReturn(new NextItemRecommendationResponse(List.of()));
        assertThat(service.recommendNextItem(request(null, List.of()), Language.KO_KR).recommendedItems()).isEmpty();
        verifyNoInteractions(component, champions, compositions);
    }

    @Test
    void Core의_실패를_빈_정상_응답으로_바꾸지_않는다() {
        var failure = new NextItemRecommendationNotFoundException("Garen", "TOP");
        when(core.recommendNextItem(any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> service.recommendNextItem(request(null, List.of()), Language.KO_KR)).isSameAs(failure);
        verifyNoInteractions(component, champions, compositions);
    }

    @Test
    void 목표_실패나_결과_누락은_Core를_제거하지_않는다() {
        prepareCore(List.of(coreItem(3133), coreItem(3086), coreItem(3071)));
        when(component.recommend(any(), eq(catalog))).thenReturn(List.of(
                new TargetRecommendation(3133, Status.CALCULATION_FAILED, List.of(), "internal failure"),
                new TargetRecommendation(3086, Status.OK, List.of(), null)));
        var response = service.recommendNextItem(request(null, List.of()), Language.KO_KR);
        assertThat(response.recommendedItems()).hasSize(3).allSatisfy(item -> assertThat(item.purchaseBundle()).isNull());
    }

    @Test
    void 표시_정보가_없는_묶음만_제외하고_다른_Core의_묶음은_유지한다() {
        prepareCore(List.of(coreItem(3133), coreItem(3086)));
        when(component.recommend(any(), eq(catalog))).thenReturn(List.of(
                new TargetRecommendation(3133, Status.OK, List.of(purchase(999, 1), purchase(1036, 1)), null),
                new TargetRecommendation(3086, Status.OK, List.of(purchase(1052, 1)), null)));
        var response = service.recommendNextItem(request(null, List.of()), Language.KO_KR);
        assertThat(response.recommendedItems().getFirst().purchaseBundle()).isNull();
        assertThat(response.recommendedItems().getLast().purchaseBundle()).isNotNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            KO_KR | 처형인의 대검 | 워윅
            EN_US | "Executioner's Calling" | Warwick
            """)
    void 상대_조합_근거가_있으면_효과당_하나의_상황_대응_아이템을_요청_언어로_함께_반환한다(
            Language language, String itemName, String championName) {
        prepareCore(List.of(coreItem(3133)));
        Champion warwick = mock(Champion.class);
        when(warwick.getChampionId()).thenReturn(7L);
        when(warwick.getName()).thenReturn(Map.of("ko-KR", "워윅", "en-US", "Warwick"));
        when(warwick.getRiotKey()).thenReturn("Warwick");
        when(champions.findChampionByName("champion7")).thenReturn(warwick);
        when(component.recommend(any(), eq(catalog))).thenReturn(List.of(
                new TargetRecommendation(3133, Status.OK, List.of(purchase(1036, 1)), null)));
        var lookup = new dfgg.domain.match.SituationalComponentLookup("16.18",
                Map.of(new dfgg.domain.match.SituationalComponentLookup.LiftKey(3123, 7, "TOP"), new dfgg.domain.match.SituationalComponentLookup.Lift(40, 3.0),
                        new dfgg.domain.match.SituationalComponentLookup.LiftKey(3076, 7, "TOP"), new dfgg.domain.match.SituationalComponentLookup.Lift(40, 2.5)),
                Map.of(new dfgg.domain.match.SituationalComponentLookup.BaseKey(86, "TOP", 3123), .05,
                        new dfgg.domain.match.SituationalComponentLookup.BaseKey(86, "TOP", 3076), .10));
        var situational = dfgg.application.recommend.situational.SituationalComponentRuntime.available(
                lookup, new dfgg.domain.match.SituationalThresholds(2.0, .01));

        var response = service(ComponentRecommendationRuntime.available(component, catalog), situational)
                .recommendNextItem(request(null, List.of(new InventoryItemRequest(1036L, 2))), language);

        assertThat(response.recommendedItems()).hasSize(1);
        assertThat(response.situationalItems()).singleElement().satisfies(item -> {
            assertThat(item.itemId()).isEqualTo(3123L);
            assertThat(item.name()).isEqualTo(itemName);
            assertThat(item.effect()).isEqualTo("GRIEVOUS_WOUNDS");
            assertThat(item.reasonChampions()).extracting(ChampionRefDto::name).containsExactly(championName);
            assertThat(item.additionalCost()).isEqualTo(800);
            assertThat(item.affordable()).isTrue();
        });
    }

    @Test
    void 상황_대응_조회표가_없으면_빈_목록을_반환한다() {
        prepareCore(List.of(coreItem(3133)));
        when(component.recommend(any(), eq(catalog))).thenReturn(List.of(
                new TargetRecommendation(3133, Status.OK, List.of(purchase(1036, 1)), null)));
        assertThat(service.recommendNextItem(request(null, List.of()), Language.KO_KR).situationalItems()).isEmpty();
    }

    @Test
    void 구매_수량과_실행_순서가_모순인_묶음은_반환하지_않는다() {
        prepareCore(List.of(coreItem(3133)));
        var candidate = new ComponentCandidateFeatures(Map.of(1036, 1, 1052, 1), List.of(1036), Map.of(), features());
        when(component.recommend(any(), eq(catalog))).thenReturn(List.of(
                new TargetRecommendation(3133, Status.OK, List.of(new ScoredPurchase(candidate, 750, 1, 1)), null)));
        assertThat(service.recommendNextItem(request(null, List.of()), Language.KO_KR).recommendedItems().getFirst().purchaseBundle()).isNull();
    }

    private void prepareCore(List<RecommendedItemDto> items) {
        when(core.recommendNextItem(any(), any())).thenReturn(new NextItemRecommendationResponse(items));
        for (int id = 1; id <= 9; id++) champion("champion" + id, id);
        champion("Garen", 86);
        when(compositions.calculate(anyList(), anyList())).thenReturn(NO_TAGS);
    }

    private void champion(String name, long id) {
        Champion champion = mock(Champion.class);
        when(champion.getChampionId()).thenReturn(id);
        when(champions.findChampionByName(name)).thenReturn(champion);
    }

    private RecommendedItemDto coreItem(long id) {
        return new RecommendedItemDto(id, "core" + id, "core-image" + id,
                new RecommendationDescription(List.of(), List.of(), List.of("trait")));
    }

    private List<Double> features() {
        return java.util.Collections.nCopies(ComponentCandidateFeatures.FEATURE_NAMES.size(), 0.0);
    }

    private ScoredPurchase purchase(int id, int quantity) {
        return new ScoredPurchase(new ComponentCandidateFeatures(Map.of(id, quantity),
                java.util.Collections.nCopies(quantity, id), Map.of(), features()), 350L * quantity, 1, 1);
    }

    private NextItemRecommendationV4Service service(ComponentRecommendationRuntime runtime) {
        return service(runtime, dfgg.application.recommend.situational.SituationalComponentRuntime.disabled());
    }

    private NextItemRecommendationV4Service service(ComponentRecommendationRuntime runtime,
            dfgg.application.recommend.situational.SituationalComponentRuntime situational) {
        return new NextItemRecommendationV4Service(core, runtime, champions, compositions,
                new ComponentPurchaseBundleMapper(images), new LeagueOfLegendsVersionProperties("16.18"),
                situational, new dfgg.application.recommend.situational.SituationalItemMapper(images));
    }

    private NextItemRecommendationV4Request request(String patch, List<InventoryItemRequest> inventory) {
        var allies = java.util.stream.IntStream.rangeClosed(1, 4).mapToObj(i -> new ChampionDto("champion" + i, "TOP")).toList();
        var enemies = java.util.stream.IntStream.rangeClosed(5, 9).mapToObj(i -> new ChampionDto("champion" + i, "TOP")).toList();
        return new NextItemRecommendationV4Request(new ChampionDto("Garen", "TOP"), List.of(3031L), allies,
                enemies, "EMERALD", patch, inventory, 1000L, 600_000L);
    }
}
