package dfgg.application.recommend.component;

import dfgg.domain.image.ImageUrls;
import dfgg.infrastructure.external.dto.ItemResponse;
import dfgg.presentation.dto.PurchaseBundleDto;
import dfgg.presentation.dto.PurchaseItemDto;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 목표별 최상위 구매 하나에 카탈로그의 표시 정보를 붙인다. */
@Component
public class ComponentPurchaseBundleMapper {
    private static final Logger log = LoggerFactory.getLogger(ComponentPurchaseBundleMapper.class);
    private final ImageUrls images;

    public ComponentPurchaseBundleMapper(ImageUrls images) {
        this.images = images;
    }

    public PurchaseBundleDto toDto(ComponentRecommendationService.TargetRecommendation recommendation,
                                   ItemResponse catalog) {
        if (recommendation == null) {
            log.warn("Component 목표별 추천 결과가 없습니다.");
            return null;
        }
        if (recommendation.status() != ComponentRecommendationService.Status.OK
                || recommendation.rankedPurchases().isEmpty()) {
            log.debug("Component 구매 묶음 없음: target={}, status={}, reason={}",
                    recommendation.targetItemId(), recommendation.status(), recommendation.reason());
            return null;
        }
        try {
            ScoredPurchase best = recommendation.rankedPurchases().getFirst();
            var candidate = best.candidate();
            var items = candidate.itemQuantities().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(item -> purchaseItem(item.getKey(), item.getValue(), catalog))
                    .toList();
            var order = candidate.purchaseOrder().stream().map(Integer::longValue).toList();
            return new PurchaseBundleDto(items, best.additionalCost(), order);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("Component 구매 묶음 변환 실패: target={}", recommendation.targetItemId(), exception);
            return null;
        }
    }

    private PurchaseItemDto purchaseItem(int id, Integer quantity, ItemResponse catalog) {
        var item = catalog.data().get(Integer.toString(id));
        if (item == null || quantity == null) {
            throw new IllegalStateException("구매 아이템의 카탈로그 정보 또는 수량이 없습니다: " + id);
        }
        return new PurchaseItemDto((long) id, item.name(), images.itemOf(id), quantity);
    }
}
