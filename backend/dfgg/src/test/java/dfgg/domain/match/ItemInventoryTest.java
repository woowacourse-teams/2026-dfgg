package dfgg.domain.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ItemInventoryTest {
    @Test
    void 수량을_한번에_복사하고_원본의_변경으로부터_보호한다() {
        var quantities = new HashMap<>(Map.of(1036, 2, 1001, 1));
        var inventory = ItemInventory.fromQuantities(quantities);
        quantities.clear();
        assertThat(inventory).isEqualTo(ItemInventory.empty().add(1036).add(1036).add(1001));
        assertThat(inventory.bootsInQuestSlot()).isFalse();
        assertThat(inventory.controlWardsInQuestSlot()).isFalse();
    }

    @Test
    void 큰_수량도_아이템을_하나씩_생성하지_않고_보존한다() {
        assertThat(ItemInventory.fromQuantities(Map.of(1036, Integer.MAX_VALUE)).quantityOf(1036)).isEqualTo(Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 양수가_아닌_ID나_수량은_거부한다(int invalid) {
        assertThatThrownBy(() -> ItemInventory.fromQuantities(Map.of(invalid, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ItemInventory.fromQuantities(Map.of(1036, invalid))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_목록_ID_수량은_거부한다() {
        assertThatThrownBy(() -> ItemInventory.fromQuantities(null)).isInstanceOf(IllegalArgumentException.class);
        var quantities = new HashMap<Integer, Integer>();
        quantities.put(null, 1);
        assertThatThrownBy(() -> ItemInventory.fromQuantities(quantities)).isInstanceOf(IllegalArgumentException.class);
        quantities.clear();
        quantities.put(1036, null);
        assertThatThrownBy(() -> ItemInventory.fromQuantities(quantities)).isInstanceOf(IllegalArgumentException.class);
    }
}
