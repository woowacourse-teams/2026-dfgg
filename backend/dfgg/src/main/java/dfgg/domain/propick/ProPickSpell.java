package dfgg.domain.propick;

/**
 * 소환사 주문 칸 하나. 칸 0·1은 Leaguepedia 목록 순서다.
 */
public class ProPickSpell {

    private final int slot;
    private final Integer spellId;

    public ProPickSpell(int slot, Integer spellId) {
        this.slot = slot;
        this.spellId = spellId;
    }

    public int getSlot() {
        return slot;
    }

    public Integer getSpellId() {
        return spellId;
    }
}
