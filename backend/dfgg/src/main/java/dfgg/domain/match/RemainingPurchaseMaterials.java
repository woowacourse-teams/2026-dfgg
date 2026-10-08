package dfgg.domain.match;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 레시피 대조 한 번 동안 관측된 제거 재료별 잔여 수량을 관리한다.
 * 같은 아이템이 여러 번 제거됐어도 이벤트 하나를 조합식의 두 위치에 중복 배정할 수 없게 한다.
 * {@link PurchaseMaterials}의 불변 목록에서 매번 새로 만들어지는 내부 작업 상태다.
 */
final class RemainingPurchaseMaterials {
    private final Map<Integer, Integer> counts = new HashMap<>();

    RemainingPurchaseMaterials(List<Integer> ids) {
        ids.forEach(id -> counts.merge(id, 1, Integer::sum));
    }

    /** 해당 ID의 미배정 제거 이벤트가 있으면 한 개를 조합 위치에 배정한다. */
    boolean consume(int itemId) {
        int count = counts.getOrDefault(itemId, 0);
        if (count == 0) {
            return false;
        }
        counts.put(itemId, count - 1);
        return true;
    }

    /** 관측된 제거 이벤트 중 레시피 경로에 대응하지 못한 것이 남아 있는지 확인한다. */
    boolean allConsumed() {
        return counts.values().stream().allMatch(count -> count == 0);
    }

    /** 동등한 변형 재료를 찾을 때 사용할 미배정 ID 종류만 반환한다. */
    List<Integer> availableIds() {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .toList();
    }
}
