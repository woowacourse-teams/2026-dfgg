package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.application.match.ParticipantItemPurchaseExtractor;
import dfgg.application.match.ParticipantItemPurchaseNormalizationService;
import dfgg.application.match.PurchaseCostEstimator;
import dfgg.application.match.PurchaseMaterialLinker;
import dfgg.domain.match.ItemPurchaseType;
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
        PurchaseMaterialLinker.class, PurchaseCostEstimator.class,
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
        assertThat(service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types(), catalog())).isEqualTo(3);
        assertThat(service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types(), catalog())).isEqualTo(3);
        entityManager.clear();
        var saved = purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST");
        assertThat(saved).extracting(ParticipantItemPurchase::getPurchaseType).containsExactly(
                ItemPurchaseType.COMPONENT, ItemPurchaseType.COMPONENT, ItemPurchaseType.CORE);
        assertThat(saved).extracting(ParticipantItemPurchase::getPurchaseOrder).containsExactly(1, 2, 5);
        assertThat(saved).extracting(ParticipantItemPurchase::getItemCost).containsExactly(350, 350, 850);
        assertThat(saved).allSatisfy(purchase -> assertThat(purchase.getCurrentGold()).isNull());
        assertThat(timelineRepository.findById("KR_TEST").orElseThrow().getRawData()).isEqualTo(raw);
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
        assertThat(purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST")).hasSize(3);
    }

    @Test
    void 재료_가격을_조회할_수_없으면_구매_비용을_NULL로_둔다() {
        timelineRepository.saveAndFlush(new RawMatchTimeline("KR_TEST", ParticipantItemPurchaseExtractorTest.timeline()));
        ItemResponse incomplete = new ItemResponse("16.18", "16.18.1",
                Map.of("3071", item(1200, List.of("1036"))));

        service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types(), incomplete);

        entityManager.clear();
        var saved = purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST");
        assertThat(saved).hasSize(3).extracting(ParticipantItemPurchase::getItemCost).containsOnlyNulls();
    }

    private ItemResponse catalog() {
        return new ItemResponse("16.18", "16.18.1", Map.of(
                "1036", item(350, List.of()),
                "3071", item(1200, List.of("1036"))));
    }

    private ItemData item(int totalPrice, List<String> ingredients) {
        return new ItemData("test", ingredients, List.of(), List.of(), Map.of("11", true), false, 1,
                new ItemData.Gold(totalPrice, totalPrice, true), null, true, false);
    }
}
