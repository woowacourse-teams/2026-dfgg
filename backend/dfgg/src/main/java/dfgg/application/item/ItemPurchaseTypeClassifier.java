package dfgg.application.item;

import dfgg.domain.match.ItemPurchaseType;
import dfgg.infrastructure.external.dto.ItemData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 구매 로그 전용 분류 정책. 기존 ItemService의 동기화·Core 추천 대상은 변경하지 않는다.
 */
@Component
public class ItemPurchaseTypeClassifier {

    // 구매 순번이나 Lane 태그만으로 시작 아이템을 판정하면 롱소드까지 STARTER가 된다.
    // 시작 용도로 지정한 ID를 명시한다. 없는 ID를 카탈로그에 추가하지는 않는다.
    // 신규 시작 아이템이 추가되는 패치에서는 이 서비스 분류 목록을 검토해야 한다.
    private static final Set<Integer> STARTER_IDS = Set.of(
            1054, 1055, 1056, 1082, 1083, 1086, 1120,
            1101, 1102, 1103, 1105, 1106, 1107, 3865);

    // 수호자 계열은 협곡 구매 분류 대상에서 제외한다. 카탈로그의 맵 11 표시만으로 포함하지 않는다.
    private static final Set<Integer> GUARDIAN_IDS = Set.of(2051, 3112, 3177, 3184);

    /** 패치 카탈로그를 검증한 뒤 각 아이템의 서비스용 구매 분류를 불변 Map으로 반환한다. */
    public Map<Integer, ItemPurchaseType> classify(Map<String, ItemData> catalog) {
        Map<Integer, ItemData> items = validateAndIndexCatalog(catalog);
        Map<Integer, ItemPurchaseType> result = new HashMap<>();
        items.forEach((id, data) -> result.put(id, classifyItem(id, data, items)));
        return Map.copyOf(result);
    }

    /** 겹치는 속성은 제외 대상 → 소모품 → 신발 → 시작 아이템 → 구매 불가 → 재료 → 완성품 순으로 판정한다. */
    private ItemPurchaseType classifyItem(int id, ItemData data, Map<Integer, ItemData> items) {
        // 카탈로그에 존재하지만 구매 추천의 대상이 아닌 모드 변형·다른 맵 아이템은 OTHER다.
        // 카탈로그에 없는 아이템은 Map에도 없으므로 기존 구매 추출기가 오류로 처리한다.
        if (!isRiftItem(id, data) || GUARDIAN_IDS.contains(id) || data.tags().contains("Trinket")) {
            return ItemPurchaseType.OTHER;
        }
        if (Boolean.TRUE.equals(data.consumed()) || data.tags().contains("Consumable")) {
            return ItemPurchaseType.CONSUMABLE;
        }
        if (data.tags().contains("Boots")) {
            return ItemPurchaseType.BOOTS;
        }
        if (STARTER_IDS.contains(id)) {
            return ItemPurchaseType.STARTER;
        }
        if (!Boolean.TRUE.equals(data.gold().purchasable()) || Boolean.FALSE.equals(data.inStore())) {
            return ItemPurchaseType.OTHER;
        }
        if (hasPurchasableUpgrade(data.into(), items)) {
            return ItemPurchaseType.COMPONENT;
        }
        // 레시피가 없는 특수 아이템까지 완성 Core로 추측하지 않는다.
        if (data.from() != null && !data.from().isEmpty()) {
            return ItemPurchaseType.CORE;
        }
        return ItemPurchaseType.OTHER;
    }

    /** 조합 대상 중 협곡 상점에서 구매 가능한 상위 아이템이 하나라도 있으면 재료로 본다. */
    private boolean hasPurchasableUpgrade(List<String> targetIds, Map<Integer, ItemData> items) {
        if (targetIds == null) {
            return false;
        }
        return targetIds.stream().anyMatch(targetId -> isPurchasableUpgrade(targetId, items));
    }

    /** 참조한 대상이 카탈로그에 없으면 다른 분류로 추측하지 않고 입력 오류로 처리한다. */
    private boolean isPurchasableUpgrade(String targetId, Map<Integer, ItemData> items) {
        int target = parseId(targetId);
        ItemData targetData = items.get(target);
        if (targetData == null) {
            throw new IllegalArgumentException("조합 대상 아이템이 카탈로그에 없습니다. 아이템 ID: " + target);
        }
        // 모드 전용 변형·자동 변환·숨겨진 변형을 이유로 완성품을 재료로 분류하지 않는다.
        return isRiftItem(target, targetData) && Boolean.TRUE.equals(targetData.gold().purchasable())
                && !Boolean.FALSE.equals(targetData.inStore()) && !Boolean.TRUE.equals(targetData.hideFromAll());
    }

    /** 문자열 아이템 ID를 정수로 바꾸고 분류에 필요한 원본 필드의 누락을 검사한다. */
    private Map<Integer, ItemData> validateAndIndexCatalog(Map<String, ItemData> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            throw new IllegalArgumentException("구매 분류에 사용할 아이템 카탈로그가 비어 있습니다.");
        }
        Map<Integer, ItemData> items = new HashMap<>();
        catalog.forEach((key, data) -> {
            int id = parseId(key);
            if (data == null || data.maps() == null || data.tags() == null || data.gold() == null
                    || data.gold().purchasable() == null) {
                throw new IllegalArgumentException("아이템 분류에 필요한 맵·태그·구매 가능 정보가 없습니다. 아이템 ID: " + id);
            }
            if (items.putIfAbsent(id, data) != null) {
                throw new IllegalArgumentException("중복된 아이템 ID입니다: " + id);
            }
        });
        return items;
    }

    private boolean isRiftItem(int id, ItemData data) {
        // 현재 정책은 일반 아이템 ID만 대상으로 한다. 큰 ID의 모드 변형은 맵 11 표시가 있어도 제외한다.
        return id < 10000 && Boolean.TRUE.equals(data.maps().get("11"));
    }

    private int parseId(String value) {
        try {
            int id = Integer.parseInt(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("아이템 ID는 양의 정수 문자열이어야 합니다: " + value, exception);
        }
        throw new IllegalArgumentException("아이템 ID는 양의 정수 문자열이어야 합니다: " + value);
    }
}
