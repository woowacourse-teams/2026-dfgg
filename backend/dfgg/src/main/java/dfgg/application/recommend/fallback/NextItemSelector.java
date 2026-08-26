package dfgg.application.recommend.fallback;

import java.util.List;
import java.util.Optional;

/**
 * 완성된 빌드/시퀀스에서 이미 산 아이템 개수를 인덱스 삼아 다음 아이템 하나만 뽑는다.
 * 구매 순서가 실제로 일치하는지는 검증하지 않는다 — ④⑤단계는 "항상 답을 낸다"가 목적인
 * 폴백이라 정확도보다 가용성을 우선한다.
 */
final class NextItemSelector {

    private NextItemSelector() {
    }

    static Optional<List<Long>> pick(List<Long> sequence, int purchasedItemCount) {
        if (purchasedItemCount >= sequence.size()) {
            return Optional.empty();
        }
        return Optional.of(List.of(sequence.get(purchasedItemCount)));
    }
}
