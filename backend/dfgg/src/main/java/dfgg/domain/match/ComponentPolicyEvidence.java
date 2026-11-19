package dfgg.domain.match;

import java.util.List;

/** Prepared 후보 하나가 적용된 근거. 학습과 서빙이 같은 3개 Feature로 사용한다. */
public record ComponentPolicyEvidence(int itemId, double linePurchaseRate, int games) {
    public static final List<Double> NONE = List.of(0.0, 0.0, 0.0);

    public ComponentPolicyEvidence {
        if (!PreparedComponentPolicy.items().contains(itemId)) {
            throw new IllegalArgumentException("Prepared 화이트리스트 아이템이 아닙니다: " + itemId);
        }
    }

    public List<Double> features() {
        return List.of(1.0, linePurchaseRate, Math.log1p(games));
    }
}
