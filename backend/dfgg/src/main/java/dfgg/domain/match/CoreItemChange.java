package dfgg.domain.match;

/**
 * Core 보유를 바꾸는 아이템 기록 한 건. 구매는 (없음 → 아이템), 판매는 (아이템 → 없음), 되돌리기는 (before → after)다.
 * Core가 아닌 아이템은 없음(null)으로 담는다. 시각은 비어 있을 수 있다.
 */
public record CoreItemChange(Long timestamp, Integer removedItemId, Integer addedItemId) {

    /** 빠지는 것도 더해지는 것도 Core가 아니면 보유는 바뀌지 않는다. */
    public boolean changesNothing() {
        return removedItemId == null && addedItemId == null;
    }
}
