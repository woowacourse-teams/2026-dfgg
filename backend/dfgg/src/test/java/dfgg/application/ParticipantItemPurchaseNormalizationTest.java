package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.application.match.ParticipantItemPurchaseExtractor;
import dfgg.application.match.ParticipantItemPurchaseNormalizationService;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.ParticipantItemPurchase;
import dfgg.domain.match.ParticipantItemPurchaseRepository;
import dfgg.domain.match.PurchaseCostStatus;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
import jakarta.persistence.EntityManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({ParticipantItemEventExtractor.class, ParticipantItemPurchaseExtractor.class,
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
        assertThat(service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types())).isEqualTo(3);
        assertThat(service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types())).isEqualTo(3);
        var saved = purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST");
        assertThat(saved).extracting(ParticipantItemPurchase::getPurchaseType).containsExactly(
                ItemPurchaseType.COMPONENT, ItemPurchaseType.COMPONENT, ItemPurchaseType.CORE);
        assertThat(saved).extracting(ParticipantItemPurchase::getPurchaseOrder).containsExactly(1, 2, 5);
        assertThat(timelineRepository.findById("KR_TEST").orElseThrow().getRawData()).isEqualTo(raw);
    }

    @Test
    void 검증_실패시_기존_구매를_삭제하지_않는다() {
        timelineRepository.saveAndFlush(new RawMatchTimeline("KR_TEST", ParticipantItemPurchaseExtractorTest.timeline()));
        service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types());
        assertThatThrownBy(() -> service.normalize("KR_TEST", "16.18", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST")).hasSize(3);
    }

    @Test
    void 비용_가설을_엔티티와_테이블에서_같은_상태로_읽는다() {
        timelineRepository.saveAndFlush(new RawMatchTimeline("KR_TEST", ParticipantItemPurchaseExtractorTest.timeline()));
        service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types());
        var purchase = purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST").getFirst();

        purchase.recordCostHypothesis(350);
        entityManager.flush();
        entityManager.clear();

        var persisted = purchaseRepository.findById(purchase.getId()).orElseThrow();
        assertThat(persisted.getItemCost()).isEqualTo(350);
        assertThat(persisted.getItemCostStatus()).isEqualTo(PurchaseCostStatus.HYPOTHESIS);
    }

}
