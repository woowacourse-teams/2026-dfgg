package dfgg.domain.item;

import static dfgg.domain.item.ItemTrait.ENGAGE;
import static dfgg.domain.item.ItemTrait.HEAL;
import static dfgg.domain.item.ItemTrait.PEEL;
import static dfgg.domain.item.ItemTrait.SHIELD;
import static dfgg.domain.item.ItemTrait.TEAM_BUFF;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemTraitCatalogTest {

    private final ItemTraitCatalog catalog = new ItemTraitCatalog();

    @Test
    @DisplayName("SUPPORT 핵심 아이템 ID에 수동 trait를 제공한다")
    void traitsOf_ReturnsManuallyAssignedSupportTraits() {
        // given
        Item shurelyasBattlesong = new Item(2065L, "슈렐리아의 군가");
        Item locketOfTheIronSolari = new Item(3190L, "강철의 솔라리 펜던트");
        Item moonstoneRenewer = new Item(6617L, "월석 재생기");
        Item ardentCenser = new Item(3504L, "불타는 향로");

        // when & then
        assertThat(catalog.traitsOf(shurelyasBattlesong)).containsExactly(ENGAGE);
        assertThat(catalog.traitsOf(locketOfTheIronSolari)).containsExactlyInAnyOrder(PEEL, SHIELD);
        assertThat(catalog.traitsOf(moonstoneRenewer)).containsExactlyInAnyOrder(HEAL, SHIELD);
        assertThat(catalog.traitsOf(ardentCenser)).containsExactly(TEAM_BUFF);
    }

    @Test
    @DisplayName("수동 trait가 등록되지 않은 아이템은 빈 집합을 반환한다")
    void traitsOf_WhenItemIsNotRegistered_ReturnsEmptySet() {
        // given
        Item item = new Item(1L, "등록되지 않은 아이템");

        // when
        var traits = catalog.traitsOf(item);

        // then
        assertThat(traits).isEmpty();
    }
}
