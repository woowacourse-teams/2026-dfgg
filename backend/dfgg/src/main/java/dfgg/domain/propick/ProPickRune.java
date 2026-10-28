package dfgg.domain.propick;

/**
 * 룬 칸 하나. 0 핵심, 1~3 주 트리, 4~5 보조 트리, 6~8 능력치 파편이다.
 */
public class ProPickRune {

    private final int slot;
    private final Integer runeId;

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
