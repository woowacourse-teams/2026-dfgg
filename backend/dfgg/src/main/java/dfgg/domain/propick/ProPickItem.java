package dfgg.domain.propick;

/**
 * 최종 아이템 칸 하나. 0~5 인벤토리, 6 장신구다.
 */
public class ProPickItem {

    private final int slot;
    private final Long itemId;

    public ProPickItem(int slot, Long itemId) {
        this.slot = slot;
        this.itemId = itemId;
    }

    public int getSlot() {
        return slot;
    }

    public Long getItemId() {
        return itemId;
    }
}
