package dfgg.domain.match;

import java.util.Optional;

/**
 * 구매 비용 도메인 규칙이 특정 패치의 아이템 가격과 조합 관계를 조회하는 경계다.
 * 레시피 검사와 재료 가격 합산은 이 인터페이스에만 의존하므로 Data Dragon DTO 형식을 알 필요가 없다.
 * 호출자는 같은 구매를 계산하는 동안 동일한 Data Dragon 빌드의 카탈로그를 제공해야 한다.
 */
public interface PurchaseItemCatalog {

    /** 아이템이 없거나 계산에 필요한 카탈로그 필드가 잘못됐으면 빈 결과를 돌려준다. */
    Optional<PurchaseCatalogItem> find(int itemId);
}
