package dfgg.infrastructure.external.catalog;

import dfgg.domain.match.PurchaseCatalogItem;
import dfgg.domain.match.PurchaseItemCatalog;
import dfgg.infrastructure.external.dto.ItemData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 호출자가 조회한 한 Data Dragon 빌드의 아이템 데이터를 구매 비용 도메인의 조회 경계에 연결한다.
 * Data Dragon의 문자열 ID와 nullable 필드를 {@link PurchaseCatalogItem}의 정수 ID·가격·조합 목록으로 변환한다.
 * 패치나 빌드를 직접 선택하거나 원격 API를 호출하지 않으며, 생성 시 받은 카탈로그에서 요청한 아이템만 변환한다.
 * 따라서 도메인 규칙은 외부 DTO의 형식과 결측 표현을 알지 않아도 된다.
 */
public class DataDragonPurchaseItemCatalog implements PurchaseItemCatalog {
    private final Map<String, ItemData> items;

    public DataDragonPurchaseItemCatalog(Map<String, ItemData> items) {
        if (items == null) {
            throw new IllegalArgumentException("패치별 카탈로그가 필요합니다.");
        }
        this.items = items;
    }

    /** 요청한 아이템이 없거나 가격·조합 ID가 유효하지 않으면 빈 결과를 반환한다. */
    @Override
    public Optional<PurchaseCatalogItem> find(int itemId) {
        ItemData data = items.get(Integer.toString(itemId));
        if (data == null || data.gold() == null || data.gold().total() == null) {
            return Optional.empty();
        }
        try {
            PurchaseCatalogItem item = new PurchaseCatalogItem(
                    itemId,
                    data.gold().total(),
                    Boolean.TRUE.equals(data.gold().purchasable()),
                    parseIds(data.from()),
                    parseIds(data.into())
            );
            return Optional.of(item);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Data Dragon의 nullable from/into 목록을 양의 정수 ID 목록으로 변환한다. null 목록은 빈 목록으로 취급한다. */
    private List<Integer> parseIds(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalArgumentException("조합 아이템 ID가 없습니다.");
            }
            int id = Integer.parseInt(value);
            if (id <= 0) {
                throw new IllegalArgumentException("조합 아이템 ID는 양수여야 합니다.");
            }
            ids.add(id);
        }
        return List.copyOf(ids);
    }
}
