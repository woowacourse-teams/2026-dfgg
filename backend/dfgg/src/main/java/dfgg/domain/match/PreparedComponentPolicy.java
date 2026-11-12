package dfgg.domain.match;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 내 챔피언이 원래 그 계열 완성품으로 가기 때문에 미리 사두는 재료. 빈도가 아니라 정책으로 정한다.
 * 신규 패치에서는 이 목록과 정책 조회표를 함께 검토해야 한다.
 */
public final class PreparedComponentPolicy {
    public static final int MIN_GAMES = 50;
    public static final double MIN_LINE_PURCHASE_RATE = 0.30;

    // 재료 → 적용 판정에 쓰는 상위 완성품. 변환 결과물(무라마나 등)은 상점 구매가 아니어서 판정에 넣지 않는다.
    public static final Map<Integer, List<Integer>> LINES = Map.of(3070, List.of(3003, 3004, 3119));
    // 이미 보유하면 다시 추천하지 않는 변환 결과물: 대천사의 포옹, 무라마나, 영원한 겨울.
    private static final Set<Integer> TRANSFORMED = Set.of(3040, 3042, 3121);

    private PreparedComponentPolicy() {}

    public static Set<Integer> items() {
        return LINES.keySet();
    }

    /** 재료 자체, 상위 완성품, 변환 결과물 중 하나라도 보유하면 이미 그 계열에 들어선 것이다. */
    public static boolean alreadyInLine(int itemId, ItemInventory inventory) {
        if (inventory.quantityOf(itemId) > 0) return true;
        return LINES.get(itemId).stream().anyMatch(id -> inventory.quantityOf(id) > 0)
                || TRANSFORMED.stream().anyMatch(id -> inventory.quantityOf(id) > 0);
    }
}
