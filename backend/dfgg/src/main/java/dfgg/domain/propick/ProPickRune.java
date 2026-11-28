package dfgg.domain.propick;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 룬 칸 하나. 0 핵심, 1~3 주 트리, 4~5 보조 트리, 6~8 능력치 파편이다.
 */
@Embeddable
public class ProPickRune {

    @Column(name = "slot", nullable = false)
    private int slot;

    @Column(name = "rune_id", nullable = false)
    private Integer runeId;

    protected ProPickRune() {
    }

    public ProPickRune(int slot, Integer runeId) {
        this.slot = slot;
        this.runeId = runeId;
    }

    public int getSlot() {
        return slot;
    }

    public Integer getRuneId() {
        return runeId;
    }
}
