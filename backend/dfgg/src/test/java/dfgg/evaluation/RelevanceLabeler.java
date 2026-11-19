package dfgg.evaluation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LTR 학습용 등급 라벨. "구매하지 않음 = 나쁜 아이템"으로 해석하지 않는다.
 * <p>
 * 이진 라벨(샀다/안 샀다)을 쓰면 모델이 "이 판에서 선택되지 않은 모든 아이템은 나쁘다"를
 * 배운다. 실제로는 그 시점에 살 만한 아이템이 여럿이고 플레이어는 그중 하나를 고른 것뿐이다.
 * 등급을 나눠 "안 샀지만 나쁘지 않음"을 표현한다.
 *
 * <table>
 *   <tr><td>3</td><td>이 시점에 실제로 산 아이템</td></tr>
 *   <tr><td>2</td><td>같은 게임에서 나중에 산 아이템 — 지금은 아니어도 결국 고른 선택</td></tr>
 *   <tr><td>1</td><td>이 판엔 없지만 이 챔피언이 흔히 사는 아이템 — 그럴듯한 대안</td></tr>
 *   <tr><td>0</td><td>나머지</td></tr>
 * </table>
 *
 * <p>실제 관측이 통계보다 우선한다 — 정답이나 이후 구매 아이템은 base rate가 낮아도 등급이
 * 내려가지 않는다. 비주류 빌드를 통계로 깎아내리면 AD 르블랑 같은 정답을 학습에서 지우게 된다.
 */
public final class RelevanceLabeler {

    public static final int GROUND_TRUTH_LABEL = 3;
    private static final int LATER_PURCHASE_LABEL = 2;
    private static final int PLAUSIBLE_ALTERNATIVE_LABEL = 1;
    private static final int IRRELEVANT_LABEL = 0;

    private final double plausibleAlternativeThreshold;

    public RelevanceLabeler(double plausibleAlternativeThreshold) {
        this.plausibleAlternativeThreshold = plausibleAlternativeThreshold;
    }

    public int label(long itemId, Context context) {
        if (itemId == context.groundTruthItemId()) {
            return GROUND_TRUTH_LABEL;
        }
        if (context.laterPurchasedItemIds().contains(itemId)) {
            return LATER_PURCHASE_LABEL;
        }
        double baseRate = context.baseRateByItemId().getOrDefault(itemId, 0.0);
        if (baseRate >= plausibleAlternativeThreshold) {
            return PLAUSIBLE_ALTERNATIVE_LABEL;
        }
        return IRRELEVANT_LABEL;
    }

    /**
     * @param laterPurchasedItemIds 같은 게임에서 이 시점 <b>이후에</b> 산 아이템
     * @param baseRateByItemId      이 챔피언·포지션의 아이템별 구매율
     */
    public record Context(
            long groundTruthItemId,
            Set<Long> laterPurchasedItemIds,
            Map<Long, Double> baseRateByItemId
    ) {

        public Context(
                long groundTruthItemId,
                List<Long> laterPurchasedItemIds,
                Map<Long, Double> baseRateByItemId
        ) {
            this(groundTruthItemId, Set.copyOf(laterPurchasedItemIds), Map.copyOf(baseRateByItemId));
        }
    }
}
