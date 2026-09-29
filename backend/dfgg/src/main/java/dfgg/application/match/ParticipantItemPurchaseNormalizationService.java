package dfgg.application.match;

import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.ParticipantItemPurchaseRepository;
import dfgg.domain.match.RawMatchTimelineRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기존 참가자 정규화와 독립적인 한 경기 Component/Core 구매 적재 경로다. */
@Service
public class ParticipantItemPurchaseNormalizationService {

    private final RawMatchTimelineRepository timelineRepository;
    private final ParticipantItemPurchaseRepository purchaseRepository;
    private final ParticipantItemPurchaseExtractor extractor;

    public ParticipantItemPurchaseNormalizationService(
            RawMatchTimelineRepository timelineRepository,
            ParticipantItemPurchaseRepository purchaseRepository,
            ParticipantItemPurchaseExtractor extractor
    ) {
        this.timelineRepository = timelineRepository;
        this.purchaseRepository = purchaseRepository;
        this.extractor = extractor;
    }

    @Transactional
    public int normalize(String matchId, String patch, Map<Integer, ItemPurchaseType> purchaseTypes) {
        // 같은 경기의 동시 재적재를 Raw 행 잠금으로 직렬화한다. 원본은 수정하지 않는다.
        var timeline = timelineRepository.findForPurchaseNormalization(matchId)
                .orElseThrow(() -> new IllegalArgumentException("저장된 Raw Timeline이 없습니다. 경기 ID: " + matchId));
        var purchases = extractor.extract(matchId, timeline.getRawData(), patch, purchaseTypes);
        // 검증을 끝낸 뒤 구매만 교체한다. 실패 시 기존 구매도 트랜잭션으로 복구된다.
        purchaseRepository.deletePurchasesByMatchId(matchId);
        purchaseRepository.saveAllAndFlush(purchases);
        return purchases.size();
    }
}
