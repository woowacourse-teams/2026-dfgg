package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.application.match.ParticipantItemPurchaseExtractor;
import dfgg.application.match.ParticipantItemPurchaseNormalizationService;
import dfgg.application.match.PurchaseCostEstimator;
import dfgg.application.match.PurchaseGoldRangeCalculator;
import dfgg.application.match.PurchaseMaterialLinker;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.GoldRangeStatus;
import dfgg.domain.match.ParticipantItemPurchase;
import dfgg.domain.match.ParticipantItemPurchaseRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({ParticipantItemEventExtractor.class, ParticipantItemPurchaseExtractor.class,
        PurchaseMaterialLinker.class, PurchaseCostEstimator.class, PurchaseGoldRangeCalculator.class,
        ParticipantItemPurchaseNormalizationService.class})
class ParticipantItemPurchaseNormalizationTest {

    @Autowired private RawMatchTimelineRepository timelineRepository;
    @Autowired private ParticipantItemPurchaseRepository purchaseRepository;
    @Autowired private ParticipantItemPurchaseNormalizationService service;
    @Autowired private EntityManager entityManager;

    @Test
    void 재실행해도_중복_구매가_없고_Raw는_유지한다() {
        String raw = ParticipantItemPurchaseExtractorTest.timeline();
        timelineRepository.saveAndFlush(new RawMatchTimeline("KR_TEST", raw));
        assertThat(service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types(), catalog())).isEqualTo(6);
        assertThat(service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types(), catalog())).isEqualTo(6);
        entityManager.clear();
        var saved = purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST");
        assertThat(saved).extracting(ParticipantItemPurchase::getPurchaseType).containsExactly(
                ItemPurchaseType.COMPONENT, ItemPurchaseType.COMPONENT, ItemPurchaseType.CONSUMABLE,
                ItemPurchaseType.OTHER, ItemPurchaseType.CORE, ItemPurchaseType.BOOTS);
        assertThat(saved).extracting(ParticipantItemPurchase::getPurchaseOrder).containsExactly(1, 2, 3, 4, 5, 1);
        assertThat(saved).extracting(ParticipantItemPurchase::getItemCost)
                .containsExactly(350, 350, null, null, 850, null);
        assertThat(saved).allSatisfy(purchase -> {
            assertThat(purchase.getGoldLower()).isNull();
            assertThat(purchase.getGoldUpper()).isNull();
            assertThat(purchase.getGoldRangeStatus()).isNull();
        });
        assertThat(timelineRepository.findById("KR_TEST").orElseThrow().getRawData()).isEqualTo(raw);
    }

    @Test
    void totalGold와_거래가_양립하면_보정한_구매_골드_범위를_DB에_저장한다() {
        timelineRepository.saveAndFlush(new RawMatchTimeline("KR_TEST", goldTimeline(true)));

        service.normalize("KR_TEST", "16.18", Map.of(1036, ItemPurchaseType.COMPONENT), catalog());

        entityManager.clear();
        ParticipantItemPurchase saved = purchaseRepository
                .findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST").getFirst();
        assertThat(saved.getGoldLower()).isEqualTo(500);
        assertThat(saved.getGoldUpper()).isEqualTo(1_000);
        assertThat(saved.getGoldRangeStatus()).isEqualTo(GoldRangeStatus.TOTAL_GOLD_REFINED);
    }

    @Test
    void totalGold가_없으면_기본_범위를_DB에_저장한다() {
        timelineRepository.saveAndFlush(new RawMatchTimeline("KR_TEST", goldTimeline(false)));

        service.normalize("KR_TEST", "16.18", Map.of(1036, ItemPurchaseType.COMPONENT), catalog());

        entityManager.clear();
        ParticipantItemPurchase saved = purchaseRepository
                .findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST").getFirst();
        assertThat(saved.getGoldLower()).isEqualTo(500);
        assertThat(saved.getGoldUpper()).isEqualTo(1_250);
        assertThat(saved.getGoldRangeStatus()).isEqualTo(GoldRangeStatus.BASE);
    }

    @Test
    void 검증_실패시_기존_구매를_삭제하지_않는다() {
        timelineRepository.saveAndFlush(new RawMatchTimeline("KR_TEST", ParticipantItemPurchaseExtractorTest.timeline()));
        service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types(), catalog());
        assertThatThrownBy(() -> service.normalize("KR_TEST", "16.18", Map.of(), catalog()))
                .isInstanceOf(IllegalArgumentException.class);
        ItemResponse anotherPatch = new ItemResponse("16.19", "16.19.1", catalog().data());
        assertThatThrownBy(() -> service.normalize(
                "KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types(), anotherPatch))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST")).hasSize(6);
    }

    @Test
    void 재료_가격을_조회할_수_없으면_구매_비용을_NULL로_둔다() {
        timelineRepository.saveAndFlush(new RawMatchTimeline("KR_TEST", ParticipantItemPurchaseExtractorTest.timeline()));
        ItemResponse incomplete = new ItemResponse("16.18", "16.18.1",
                Map.of("3071", item(1200, List.of("1036"))));

        service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types(), incomplete);

        entityManager.clear();
        var saved = purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST");
        assertThat(saved).hasSize(6).extracting(ParticipantItemPurchase::getItemCost).containsOnlyNulls();
    }

    private ItemResponse catalog() {
        return new ItemResponse("16.18", "16.18.1", Map.of(
                "1036", item(350, List.of()),
                "3071", item(1200, List.of("1036"))));
    }

    private String goldTimeline(boolean includeTotalGold) {
        String start = "{\"currentGold\":500}";
        String end = "{\"currentGold\":900}";
        if (includeTotalGold) {
            start = "{\"currentGold\":500,\"totalGold\":1000}";
            end = "{\"currentGold\":900,\"totalGold\":1500}";
        }
        return "{\"metadata\":{\"matchId\":\"KR_TEST\"},\"info\":{\"frames\":["
                + "{\"participantFrames\":{\"1\":" + start + "},\"events\":[]},"
                + "{\"participantFrames\":{\"1\":" + end + "},\"events\":["
                + "{\"type\":\"ITEM_PURCHASED\",\"participantId\":1,\"itemId\":1036,\"timestamp\":100}"
                + "]}]}}";
    }

    private ItemData item(int totalPrice, List<String> ingredients) {
        return new ItemData("test", ingredients, List.of(), List.of(), Map.of("11", true), false, 1,
                new ItemData.Gold(totalPrice, totalPrice, true), null, true, false);
    }
}
