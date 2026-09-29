package dfgg.application.match;

import dfgg.domain.match.GoldRangeRefinement;
import dfgg.domain.match.GoldRangeStatus;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.domain.match.ParticipantItemEvent;
import dfgg.domain.match.ParticipantItemPurchase;
import dfgg.domain.match.ParticipantItemPurchaseRepository;
import dfgg.domain.match.RawMatchTimelineRepository;
import dfgg.infrastructure.external.dto.ItemData;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기존 참가자 정규화와 독립적인 한 경기 전체 구매 적재 경로다. */
@Service
public class ParticipantItemPurchaseNormalizationService {

    private final RawMatchTimelineRepository timelineRepository;
    private final ParticipantItemPurchaseRepository purchaseRepository;
    private final ParticipantItemEventExtractor eventExtractor;
    private final ParticipantItemPurchaseExtractor extractor;
    private final PurchaseMaterialLinker materialLinker;
    private final PurchaseCostEstimator costEstimator;
    private final PurchaseGoldRangeCalculator goldRangeCalculator;

    public ParticipantItemPurchaseNormalizationService(
            RawMatchTimelineRepository timelineRepository,
            ParticipantItemPurchaseRepository purchaseRepository,
            ParticipantItemEventExtractor eventExtractor,
            ParticipantItemPurchaseExtractor extractor,
            PurchaseMaterialLinker materialLinker,
            PurchaseCostEstimator costEstimator,
            PurchaseGoldRangeCalculator goldRangeCalculator
    ) {
        this.timelineRepository = timelineRepository;
        this.purchaseRepository = purchaseRepository;
        this.eventExtractor = eventExtractor;
        this.extractor = extractor;
        this.materialLinker = materialLinker;
        this.costEstimator = costEstimator;
        this.goldRangeCalculator = goldRangeCalculator;
    }

    /**
     * 한 경기의 전체 구매와 계산 가능한 추가 지출액·구매 직전 골드 범위를 함께 적재한다.
     * purchaseTypes는 호출자가 catalog과 동일한 Data Dragon 빌드에서 만든 분류여야 한다.
     */
    @Transactional
    public int normalize(String matchId, String patch, Map<Integer, ItemPurchaseType> purchaseTypes,
                         ItemResponse catalog) {
        validateCatalog(patch, catalog);
        // 같은 경기의 동시 재적재를 Raw 행 잠금으로 직렬화한다. 원본은 수정하지 않는다.
        var timeline = timelineRepository.findForPurchaseNormalization(matchId)
                .orElseThrow(() -> new IllegalArgumentException("저장된 Raw Timeline이 없습니다. 경기 ID: " + matchId));
        List<ParticipantItemEvent> events = eventExtractor.extract(matchId, timeline.getRawData());
        List<ParticipantItemPurchase> purchases = extractor.extractFromEvents(events, patch, purchaseTypes);
        Map<Integer, List<ParticipantItemEvent>> eventsByParticipant = groupByParticipant(events);
        recordCosts(purchases, eventsByParticipant, catalog.data());
        recordGoldRanges(purchases, eventsByParticipant,
                goldRangeCalculator.refineWithTotalGold(matchId, timeline.getRawData(), events, catalog));
        // 검증을 끝낸 뒤 구매만 교체한다. 실패 시 기존 구매도 트랜잭션으로 복구된다.
        purchaseRepository.deletePurchasesByMatchId(matchId);
        purchaseRepository.saveAllAndFlush(purchases);
        return purchases.size();
    }

    /** 요청한 패치와 비용 계산 카탈로그의 패치·빌드가 다르면 적재를 막는다. */
    private void validateCatalog(String patch, ItemResponse catalog) {
        if (patch == null || catalog == null || catalog.data() == null || catalog.data().isEmpty()
                || !patch.equals(catalog.version()) || catalog.dataVersion() == null
                || !catalog.dataVersion().matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || !catalog.dataVersion().startsWith(patch + ".")) {
            throw new IllegalArgumentException("구매 분류 패치와 일치하는 Data Dragon 빌드 카탈로그가 필요합니다.");
        }
    }

    /** 참가자별 원천 이벤트를 모아 각 구매의 제거 재료와 추가 지출액을 연결한다. */
    private void recordCosts(List<ParticipantItemPurchase> purchases,
                             Map<Integer, List<ParticipantItemEvent>> eventsByParticipant,
                             Map<String, ItemData> catalog) {
        for (ParticipantItemPurchase purchase : purchases) {
            List<ParticipantItemEvent> participantEvents = eventsByParticipant.get(purchase.getParticipantId());
            ParticipantItemEvent source = sourceEvent(purchase, participantEvents);
            Optional<List<Integer>> materials = materialLinker.link(source, participantEvents);
            if (materials.isEmpty()) {
                continue;
            }
            OptionalInt cost = costEstimator.estimate(purchase.getItemId(), materials.orElseThrow(), catalog);
            if (cost.isPresent()) {
                purchase.recordCostHypothesis(cost.getAsInt());
            }
        }
    }

    /** totalGold와 거래 흐름이 양립하면 보정 범위를, 아니면 기본 범위를 계산 근거와 함께 기록한다. */
    private void recordGoldRanges(List<ParticipantItemPurchase> purchases,
                                  Map<Integer, List<ParticipantItemEvent>> eventsByParticipant,
                                  Map<ParticipantItemEvent, GoldRangeRefinement> refinements) {
        for (ParticipantItemPurchase purchase : purchases) {
            ParticipantItemEvent source = sourceEvent(purchase,
                    eventsByParticipant.get(purchase.getParticipantId()));
            GoldRangeRefinement refinement = refinements.get(source);
            if (refinement == null) {
                continue;
            }
            if (refinement.totalGoldRefinement().isPresent()) {
                purchase.recordGoldRange(refinement.totalGoldRefinement().orElseThrow(),
                        GoldRangeStatus.TOTAL_GOLD_REFINED);
                continue;
            }
            purchase.recordGoldRange(refinement.base(), GoldRangeStatus.BASE);
        }
    }

    /** 구매와 같은 참가자의 eventOrder 순서를 유지한다. */
    private Map<Integer, List<ParticipantItemEvent>> groupByParticipant(List<ParticipantItemEvent> events) {
        Map<Integer, List<ParticipantItemEvent>> grouped = new HashMap<>();
        for (ParticipantItemEvent event : events) {
            grouped.computeIfAbsent(event.participantId(), ignored -> new ArrayList<>()).add(event);
        }
        return grouped;
    }

    /** 저장 대상 구매의 원천 purchaseOrder를 이용해 같은 이벤트를 다시 찾는다. */
    private ParticipantItemEvent sourceEvent(ParticipantItemPurchase purchase,
                                             List<ParticipantItemEvent> participantEvents) {
        if (participantEvents == null) {
            throw new IllegalStateException("구매 참가자의 원천 아이템 이벤트가 없습니다.");
        }
        for (ParticipantItemEvent event : participantEvents) {
            if ("ITEM_PURCHASED".equals(event.eventType()) && event.purchaseOrder() != null
                    && event.purchaseOrder() == purchase.getPurchaseOrder()
                    && event.itemId() != null && event.itemId() == purchase.getItemId()) {
                return event;
            }
        }
        throw new IllegalStateException("저장 대상 구매의 원천 아이템 이벤트를 찾을 수 없습니다.");
    }
}
