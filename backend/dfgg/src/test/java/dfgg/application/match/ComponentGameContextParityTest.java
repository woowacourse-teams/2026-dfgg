package dfgg.application.match;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.recommend.NextItemRecommendationService;
import dfgg.application.recommend.component.*;
import dfgg.application.recommend.context.*;
import dfgg.application.recommend.v3.RecommendationQuery;
import dfgg.application.recommend.v3.feature.*;
import dfgg.application.recommend.v3.ranker.GradientBoostedTrees;
import dfgg.application.champion.ChampionService;
import dfgg.domain.champion.*;
import dfgg.domain.language.Language;
import dfgg.domain.match.*;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.*;
import dfgg.presentation.dto.request.NextItemRecommendationRequest;
import dfgg.presentation.dto.response.NextItemRecommendationResponse;
import java.util.*;
import org.junit.jupiter.api.Test;

class ComponentGameContextParityTest {
    @Test
    void training_serving_and_core_share_counts_exclude_self_and_reuse_them_for_all_candidates() throws Exception {
        var championRepository = mock(ChampionRepository.class);
        var champions = List.of(champion(1, ChampionTag.TANK), champion(2, ChampionTag.MAGE, ChampionTag.SUPPORT),
                champion(3, ChampionTag.TANK, ChampionTag.FIGHTER), champion(4, ChampionTag.MARKSMAN, ChampionTag.ASSASSIN));
        when(championRepository.findAllWithTagsByChampionIdIn(any())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return champions.stream().filter(c -> ids.contains(c.getChampionId())).toList();
        });
        var calculator = new CompositionFeatureCalculator(championRepository);
        var participants = mock(NormalizedMatchParticipantRepository.class);
        var roster = List.of(participant(1, 100, 1), participant(2, 100, 2),
                participant(3, 200, 3), participant(4, 200, 4), participant(5, 200, 999));
        when(participants.findByMatchId("M")).thenReturn(roster);
        var reader = new ComponentCompositionReader(participants, calculator);
        var composition = reader.forMatch("M").get(1);
        // Self TANK is excluded; multi-tag contributes to both; unknown 999 contributes zero.
        assertThat(composition.values()).containsExactly(1., 0., 1., 1., 1., 0., 1., 0., 1.);
        var query = new RecommendationQuery(1L, ChampionPosition.TOP, List.of(), List.of(2L),
                List.of(3L, 4L, 999L), null, "16.18");
        var coreVector = FeatureVector.empty();
        new QueryFeatureExtractor(calculator).extract(query, coreVector);
        for (int i = 0; i < 9; i++) {
            assertThat(coreVector.get(FeatureName.valueOf(CompositionFeatures.FEATURE_NAMES.get(i).toUpperCase(Locale.ROOT))))
                    .isEqualTo(composition.values().get(i));
        }
        var response = new ObjectMapper().readValue("""
                {"version":"16.18.1","data":{
                "101":{"gold":{"total":300,"purchasable":true},"stats":{"FlatArmorMod":15}},
                "102":{"gold":{"total":300,"purchasable":true},"stats":{"FlatSpellBlockMod":20}},
                "500":{"from":["101","102"],"gold":{"total":1000,"purchasable":true}}}}
                """, ItemResponse.class);
        var first = purchase(101, 1, ItemPurchaseType.COMPONENT, ItemInventory.empty());
        var target = purchase(500, 2, ItemPurchaseType.CORE, ItemInventory.empty().add(101));
        var snapshots = mock(ParticipantItemPurchaseSnapshotService.class);
        when(snapshots.prepareForMatch("M")).thenReturn(new ParticipantItemPurchaseSnapshotService.PreparedPurchases(
                List.of(first, target), List.of(new ParticipantItemPurchaseBundle(List.of(first), Set.of()))));
        var trained = new ComponentTrainingDataService(snapshots, new ObjectMapper(), ComponentPolicyLookup.disabled(), reader)
                .examplesForMatch("M", response).getFirst();
        assertThat(trained.status()).isEqualTo("READY");

        var core = mock(NextItemRecommendationService.class);
        var championService = mock(ChampionService.class);
        for (var c : champions) when(championService.findChampionByName(c.getRiotKey())).thenReturn(c);
        when(championService.findChampionByName("C999")).thenReturn(champion(999));
        var request = new NextItemRecommendationRequest(new ChampionDto("C1", "TOP"), List.of(),
                List.of(new ChampionDto("C2", "MID")), List.of(new ChampionDto("C3", "TOP"),
                new ChampionDto("C4", "BOTTOM"), new ChampionDto("C999", "MID")), null, "16.18");
        when(core.recommendNextItem(request, Language.KO_KR)).thenReturn(new NextItemRecommendationResponse(
                List.of(new RecommendedItemDto(500L, "target", "image", null))));
        clearInvocations(championRepository);
        var serving = new ComponentRecommendationService(mock(GradientBoostedTrees.class));
        var served = new CoreComponentRecommendationService(core, serving, championService, calculator)
                .recommend(request, ItemInventory.empty(), 600, 20_000, response).getFirst().component();
        assertThat(served.status()).isEqualTo(ComponentRecommendationService.Status.OK);
        assertThat(served.rankedPurchases()).hasSizeGreaterThan(1);
        assertThat(trained.candidates()).extracting(ComponentTrainingCandidate::candidate)
                .containsExactlyElementsOf(served.rankedPurchases().stream().map(ScoredPurchase::candidate).toList());
        assertThat(served.rankedPurchases()).allSatisfy(c -> {
            assertThat(c.candidate().features()).hasSize(58);
            assertThat(c.candidate().features().subList(46, 55)).isEqualTo(composition.values());
        });
        verify(championRepository, times(2)).findAllWithTagsByChampionIdIn(any());
    }

    @Test
    void missing_roster_fails_instead_of_silently_training_on_zero_context() {
        var participants = mock(NormalizedMatchParticipantRepository.class);
        var reader = new ComponentCompositionReader(participants, mock(CompositionFeatureCalculator.class));
        assertThatThrownBy(() -> reader.forMatch("missing")).isInstanceOf(IllegalArgumentException.class);
    }

    private Champion champion(long id, ChampionTag... tags) {
        return new Champion(id, "C" + id, Map.of("en", "C" + id), List.of(tags));
    }

    private NormalizedMatchParticipant participant(int id, int team, int champion) {
        var p = mock(NormalizedMatchParticipant.class);
        when(p.getParticipantId()).thenReturn(id);
        when(p.getTeamId()).thenReturn(team);
        when(p.getChampionId()).thenReturn(champion);
        return p;
    }

    private ParticipantItemPurchaseSnapshot purchase(int id, int order, ItemPurchaseType type, ItemInventory before) {
        var event = new ParticipantItemEvent("M", 1, order, order, 0, order, "ITEM_PURCHASED", id,
                null, null, order * 20_000L, "{}", "test");
        var purchase = new NormalizedMatchParticipantItemEvent(event, type, "16.18", "TOP");
        purchase.recordCostHypothesis(order == 1 ? 300 : 700);
        purchase.recordGoldRange(new GoldRange(600, 1000), GoldRangeStatus.BASE);
        return new ParticipantItemPurchaseSnapshot(purchase, event, before, ParticipantItemPurchaseSnapshot.Status.READY, null);
    }
}
