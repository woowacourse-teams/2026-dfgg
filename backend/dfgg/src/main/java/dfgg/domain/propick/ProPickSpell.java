package dfgg.domain.propick;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 소환사 주문 칸 하나. 칸 0·1은 Leaguepedia 목록 순서다.
 */
@Embeddable
public class ProPickSpell {

    @Column(name = "slot", nullable = false)
    private int slot;

    @Column(name = "spell_id", nullable = false)
    private Integer spellId;

    protected ProPickSpell() {
    }

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
