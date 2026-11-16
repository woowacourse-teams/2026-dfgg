package dfgg.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemTest {

    private static final long RABADONS_DEATHCAP = 3089L;

    @Test
    @DisplayName("가격 정보에 총가격이 있으면 그 값을 준다")
    void totalGold_WhenTotalExists_ReturnsTotal() {
        // given
        Item item = new Item(RABADONS_DEATHCAP, Map.of("ko-KR", "라바돈의 죽음모자"),
                Map.of("base", 1100, "total", 3600), List.of("SpellDamage"));

        // when
        OptionalInt totalGold = item.totalGold();

        // then
        assertThat(totalGold).hasValue(3600);
    }

    @Test
    @DisplayName("가격 정보가 아예 없으면 총가격은 없다고 답한다")
    void totalGold_WhenGoldIsMissing_ReturnsEmpty() {
        // given
        Item item = new Item(RABADONS_DEATHCAP, Map.of("ko-KR", "라바돈의 죽음모자"), List.of("SpellDamage"));

        // when
        OptionalInt totalGold = item.totalGold();

        // then
        assertThat(totalGold).isEmpty();
    }

    @Test
    @DisplayName("가격 정보는 있지만 총가격 항목이 없으면 총가격은 없다고 답한다")
    void totalGold_WhenTotalKeyIsMissing_ReturnsEmpty() {
        // given
        Item item = new Item(RABADONS_DEATHCAP, Map.of("ko-KR", "라바돈의 죽음모자"),
                Map.of("base", 1100), List.of("SpellDamage"));

        // when
        OptionalInt totalGold = item.totalGold();

        // then
        assertThat(totalGold).isEmpty();
    }
}
