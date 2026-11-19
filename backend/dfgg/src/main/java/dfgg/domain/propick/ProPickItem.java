package dfgg.domain.propick;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 최종 아이템 칸 하나. 0~5 인벤토리, 6 장신구다.
 */
@Embeddable
public class ProPickItem {

    @Column(name = "slot", nullable = false)
    private int slot;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    protected ProPickItem() {
    }

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
