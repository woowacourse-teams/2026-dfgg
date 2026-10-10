package dfgg.application.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.domain.match.GoldRange;
import dfgg.domain.match.GoldRangeStatus;
import dfgg.domain.match.ItemInventory;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.NormalizedMatchParticipantItemEvent;
import dfgg.domain.match.NormalizedMatchParticipantItemEventRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticipantItemPurchaseBundleTest {
    private dfgg.application.recommend.component.ComponentCompositionReader compositions() {
        var reader = org.mockito.Mockito.mock(dfgg.application.recommend.component.ComponentCompositionReader.class);
        org.mockito.Mockito.when(reader.forMatch("KR_TEST")).thenReturn(java.util.Map.of(1,
                dfgg.application.recommend.context.TestCompositions.NO_TAGS));
        return reader;
    }

    private final NormalizedMatchParticipantItemEventRepository repository = mock(NormalizedMatchParticipantItemEventRepository.class);
    private final PurchaseMaterialLinker linker = new PurchaseMaterialLinker();
    private final ParticipantItemPurchaseSnapshotService service = new ParticipantItemPurchaseSnapshotService(
            repository, new PurchaseInventoryReconstructor(linker), linker);

    @Test
    void 연속_구매를_수량으로_묶고_첫_구매_직전_상태를_사용한다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":3000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":5000}
                """);
        var bundle = service.bundlesForMatch("KR_TEST").getFirst();
        assertThat(bundle.ready()).isTrue();
        assertThat(bundle.itemQuantities()).containsEntry(1036, 2).containsEntry(1037, 1);
        assertThat(bundle.startPurchaseOrder()).isEqualTo(1);
        assertThat(bundle.endPurchaseOrder()).isEqualTo(3);
        assertThat(bundle.inventoryBefore()).isEqualTo(ItemInventory.empty());
        assertThat(bundle.goldLower()).isEqualTo(2000);
        assertThat(bundle.totalCost()).isEqualTo(1575L);
    }

    @Test
    void 정확히_10초_공백이면_분리하고_참가자도_섞이지_않는다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_PURCHASED","participantId":2,"itemId":1037,"timestamp":1000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":11000}
                """);
        var bundles = service.bundlesForMatch("KR_TEST");
        assertThat(bundles).hasSize(3);
        assertThat(bundles.get(1).inventoryBefore()).isEqualTo(ItemInventory.empty().add(1036));
        assertThat(bundles.get(2).participantId()).isEqualTo(2);
    }

    @Test
    void 이벤트_공백으로_경계를_정하므로_중간_제거는_묶음을_끊지_않는다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":2151,"timestamp":10000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":19000}
                """);
        assertThat(service.bundlesForMatch("KR_TEST")).hasSize(1)
                .allMatch(ParticipantItemPurchaseBundle::ready);
    }

    @Test
    void 연속_이벤트가_30초를_넘으면_잘라서_정답을_만들지_않고_전체_제외한다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":10000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":19000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":28000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":32000}
                """);
        var bundles = service.bundlesForMatch("KR_TEST");
        assertThat(bundles).hasSize(1);
        assertThat(bundles.getFirst().exclusionReasons()).contains(ParticipantItemPurchaseBundle.Reason.DURATION_EXCEEDED);
    }

    @Test
    void 판매가_섞이면_묶음을_제외하되_후속_묶음_인벤토리는_판매를_반영한다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_SOLD","participantId":1,"itemId":1036,"timestamp":2000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":12000}
                """);
        var bundles = service.bundlesForMatch("KR_TEST");
        assertThat(bundles.getFirst().exclusionReasons()).contains(ParticipantItemPurchaseBundle.Reason.SALE_INCLUDED);
        assertThat(bundles.getLast().inventoryBefore()).isEqualTo(ItemInventory.empty());
        assertThat(bundles.getLast().ready()).isTrue();
    }

    @Test
    void 취소된_구매가_있으면_부분_정답을_만들지_않는다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_UNDO","participantId":1,"beforeId":1036,"afterId":0,"timestamp":2000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":3000}
                """);
        var bundle = service.bundlesForMatch("KR_TEST").getFirst();
        assertThat(bundle.exclusionReasons()).contains(ParticipantItemPurchaseBundle.Reason.UNDO_INCLUDED,
                ParticipantItemPurchaseBundle.Reason.PURCHASE_UNDONE);
        assertThat(bundle.purchases()).hasSize(2);
    }

    @Test
    void 소모품을_빼서_묶음을_만들지_않고_혼합_묶음_전체를_제외한다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":2003,"timestamp":2000}
                """);
        var bundle = service.bundlesForMatch("KR_TEST").getFirst();
        assertThat(bundle.purchases()).hasSize(2);
        assertThat(bundle.exclusionReasons()).contains(ParticipantItemPurchaseBundle.Reason.MIXED_PURCHASE_TYPES);
    }

    @Test
    void 묶음_안에서_구매한_재료를_다시_조합하면_중복_정답으로_사용하지_않는다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036,"timestamp":2000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071,"timestamp":2000}
                """);
        assertThat(service.bundlesForMatch("KR_TEST").getFirst().exclusionReasons())
                .contains(ParticipantItemPurchaseBundle.Reason.RECOMBINATION_INCLUDED);
    }

    @Test
    void 이전_묶음부터_보유한_재료의_조합은_재조합_제외가_아니다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036,"timestamp":20000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071,"timestamp":20000}
                """);
        var bundle = service.bundlesForMatch("KR_TEST").getLast();
        assertThat(bundle.ready()).isTrue();
        assertThat(bundle.inventoryBefore()).isEqualTo(ItemInventory.empty().add(1036));
    }

    @Test
    void 골드_입력은_첫_구매에만_필요하다() {
        var rows = load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":2000}
                """, 1);
        assertThat(rows.getLast().getGoldLower()).isNull();
        assertThat(service.bundlesForMatch("KR_TEST").getFirst().ready()).isTrue();
    }

    @Test
    void 첫_구매의_골드가_없으면_묶음을_제외한다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000}
                """, 0);
        assertThat(service.bundlesForMatch("KR_TEST").getFirst().exclusionReasons())
                .contains(ParticipantItemPurchaseBundle.Reason.GOLD_RANGE_MISSING);
    }

    @Test
    void 시각이_없는_구매는_다른_구매와_함께_정답으로_사용하지_않는다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037}
                """);
        assertThat(service.bundlesForMatch("KR_TEST").getFirst().exclusionReasons())
                .contains(ParticipantItemPurchaseBundle.Reason.EVENT_TIME_INVALID);
    }

    @Test
    void 실제_Core_목표로_수량_정답과_Feature를_JSONL까지_연결한다() throws Exception {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1037,"timestamp":2000},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1036,"timestamp":20000},
                {"type":"ITEM_DESTROYED","participantId":1,"itemId":1037,"timestamp":20000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071,"timestamp":20000}
                """);
        var mapper = new ObjectMapper();
        var catalog = mapper.readValue("""
                {"version":"16.18.1","data":{
                  "1036":{"gold":{"total":350,"purchasable":true},"stats":{"FlatPhysicalDamageMod":10},"into":["3071"]},
                  "1037":{"gold":{"total":875,"purchasable":true},"stats":{"FlatPhysicalDamageMod":25},"into":["3071"]},
                  "3071":{"gold":{"total":1575,"purchasable":true},"stats":{"FlatPhysicalDamageMod":40},"from":["1036","1037"]}}}
                """, dfgg.infrastructure.external.dto.ItemResponse.class);
        var exporter = new ComponentTrainingDataService(service, mapper, dfgg.domain.match.PreparationLookup.disabled(), compositions());
        var examples = exporter.examplesForMatch("KR_TEST", catalog);
        assertThat(examples).hasSize(2).allMatch(e -> e.status().equals("READY"));
        var first = examples.getFirst();
        assertThat(first.targetCoreId()).isEqualTo(3071);
        assertThat(first.candidates().stream().filter(c -> c.label() == 1).toList()).hasSize(1);
        var positive = first.candidates().stream().filter(c -> c.label() == 1).findFirst().orElseThrow();
        assertThat(positive.candidate().itemQuantities()).containsEntry(1036, 1).containsEntry(1037, 1);
        assertThat(positive.candidate().features().subList(0, 10)).containsExactly(1225.0, 2.0, 2.0, 0.0, 2.0, 0.0, 0.0, 0.0, 2.0, 0.0);
        assertThat(positive.candidate().features().get(10)).isEqualTo(35.0);
        var completed = examples.getLast().candidates().stream().filter(c -> c.label() == 1).findFirst().orElseThrow();
        assertThat(completed.candidate().features().subList(5, 8)).containsExactly(1.0, 2.0, 1225.0);
        assertThat(completed.candidate().features().get(10)).isEqualTo(5.0); // 40 - 기존 재료 35, 완성품 40을 그대로 더하지 않는다.
        assertThat(positive.candidate().features().subList(22, 29))
                .containsExactly(1000.0 / 60_000, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        var writer = new java.io.StringWriter();
        exporter.writeJsonl("KR_TEST", catalog, writer);
        assertThat(writer.toString().lines().toList()).hasSize(2);
        var json = mapper.readTree(writer.toString().lines().findFirst().orElseThrow());
        assertThat(json.path("targetSource").asText()).isEqualTo("OBSERVED_FUTURE_TARGET_ITEM");
        assertThat(json.path("featureNames").size()).isEqualTo(59);
        var exportedCandidate = json.path("candidates").get(0);
        assertThat(exportedCandidate.path("features").size()).isEqualTo(59);
        assertThat(exportedCandidate.has("itemQuantities")).isTrue();
        assertThat(exportedCandidate.has("label")).isTrue();
        assertThat(exportedCandidate.has("candidate")).isFalse(); // Python이 읽던 평탄 JSONL 형식을 유지한다.
        assertThat(json.path("schemaVersion").asText()).isEqualTo("component-bundle-validation-v7");
        var wrongPatch = new dfgg.infrastructure.external.dto.ItemResponse("16.19.1", null, catalog.data());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> exporter.examplesForMatch("KR_TEST", wrongPatch))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 목표_Core가_없는_구매는_후보와_라벨을_억지로_만들지_않는다() throws Exception {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000}
                """);
        var mapper = new ObjectMapper();
        var catalog = new dfgg.infrastructure.external.dto.ItemResponse("16.18.1", null, java.util.Map.of());
        var example = new ComponentTrainingDataService(service, mapper, dfgg.domain.match.PreparationLookup.disabled(), compositions()).examplesForMatch("KR_TEST", catalog).getFirst();
        assertThat(example.status()).isEqualTo("NO_OBSERVED_TARGET");
        assertThat(example.candidates()).isEmpty();
    }

    @Test
    void 정답이_목표_경로에_없으면_후보를_추가하거나_전부_부정으로_만들지_않는다() throws Exception {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071,"timestamp":20000}
                """);
        var mapper = new ObjectMapper();
        var catalog = mapper.readValue("""
                {"version":"16.18.1","data":{
                  "1036":{"gold":{"total":350,"purchasable":true}},
                  "1037":{"gold":{"total":300,"purchasable":true}},
                  "3071":{"from":["1037"],"gold":{"total":350,"purchasable":true}}}}
                """, dfgg.infrastructure.external.dto.ItemResponse.class);
        var first = new ComponentTrainingDataService(service, mapper, dfgg.domain.match.PreparationLookup.disabled(), compositions()).examplesForMatch("KR_TEST", catalog).getFirst();
        assertThat(first.status()).isEqualTo("OBSERVED_BUNDLE_MISSING");
        assertThat(first.candidates()).isNotEmpty().allMatch(c -> c.label() == null);
        assertThat(first.candidates().getFirst().candidate().itemQuantities()).doesNotContainKey(1036);
    }

    @Test
    void 취소된_Core를_사후_목표로_사용하지_않는다() {
        load("""
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":1036,"timestamp":1000},
                {"type":"ITEM_PURCHASED","participantId":1,"itemId":3071,"timestamp":20000},
                {"type":"ITEM_UNDO","participantId":1,"beforeId":3071,"afterId":0,"timestamp":21000}
                """);
        var catalog = new dfgg.infrastructure.external.dto.ItemResponse("16.18.1", null, java.util.Map.of());
        var first = new ComponentTrainingDataService(service, new ObjectMapper(), dfgg.domain.match.PreparationLookup.disabled(), compositions())
                .examplesForMatch("KR_TEST", catalog).getFirst();
        assertThat(first.status()).isEqualTo("NO_OBSERVED_TARGET");
    }

    private List<NormalizedMatchParticipantItemEvent> load(String payload) {
        return load(payload, Integer.MAX_VALUE);
    }

    private List<NormalizedMatchParticipantItemEvent> load(String payload, int goldPurchaseLimit) {
        var events = new ParticipantItemEventExtractor(new ObjectMapper()).extract("KR_TEST",
                "{\"info\":{\"frames\":[{\"events\":[" + payload + "]}]}}");
        List<NormalizedMatchParticipantItemEvent> rows = new ArrayList<>();
        for (var event : events) {
            if (!"ITEM_PURCHASED".equals(event.eventType())) {
                rows.add(NormalizedMatchParticipantItemEvent.nonPurchase(event, "16.18", "TOP"));
                continue;
            }
            var type = event.itemId() == 2003 ? ItemPurchaseType.CONSUMABLE
                    : event.itemId() == 3071 ? ItemPurchaseType.CORE : ItemPurchaseType.COMPONENT;
            var row = new NormalizedMatchParticipantItemEvent(event, type, "16.18", "TOP");
            row.recordCostHypothesis(event.itemId() == 1037 ? 875 : 350);
            if (event.purchaseOrder() <= goldPurchaseLimit) {
                row.recordGoldRange(new GoldRange(2000, 2200), GoldRangeStatus.BASE);
            }
            rows.add(row);
        }
        when(repository.findByMatchIdOrderByParticipantIdAscEventOrderAsc("KR_TEST")).thenReturn(rows.stream()
                .sorted(java.util.Comparator.comparingInt(NormalizedMatchParticipantItemEvent::getParticipantId)
                        .thenComparingInt(row -> row.toParticipantItemEvent().eventOrder())).toList());
        return rows;
    }
}
