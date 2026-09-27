package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dfgg.application.match.ParticipantItemEventExtractor;
import dfgg.application.match.ParticipantItemPurchaseExtractor;
import dfgg.application.match.ParticipantItemPurchaseNormalizationService;
import dfgg.domain.match.ParticipantItemPurchaseRepository;
import dfgg.domain.match.RawMatchTimeline;
import dfgg.domain.match.RawMatchTimelineRepository;
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

    @Test
    void 재실행해도_중복_구매가_없고_Raw는_유지한다() {
        String raw = ParticipantItemPurchaseExtractorTest.timeline();
        timelineRepository.saveAndFlush(new RawMatchTimeline("KR_TEST", raw));
        assertThat(service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types())).isEqualTo(4);
        assertThat(service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types())).isEqualTo(4);
        assertThat(purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST")).hasSize(4);
        assertThat(timelineRepository.findById("KR_TEST").orElseThrow().getRawData()).isEqualTo(raw);
    }

    @Test
    void 검증_실패시_기존_구매를_삭제하지_않는다() {
        timelineRepository.saveAndFlush(new RawMatchTimeline("KR_TEST", ParticipantItemPurchaseExtractorTest.timeline()));
        service.normalize("KR_TEST", "16.18", ParticipantItemPurchaseExtractorTest.types());
        assertThatThrownBy(() -> service.normalize("KR_TEST", "16.18", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(purchaseRepository.findByMatchIdOrderByParticipantIdAscPurchaseOrderAsc("KR_TEST")).hasSize(4);
    }

}
