package dfgg.domain.item.trait;

import static dfgg.domain.item.trait.ItemTrait.LOCKET_OF_THE_IRON_SOLARI;
import static dfgg.domain.item.trait.ItemTrait.ARDENT_CENSER;
import static dfgg.domain.item.trait.ItemTrait.MOONSTONE_RENEWER;
import static dfgg.domain.item.trait.ItemTrait.SHURELYA_BATTLESONG;
import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.item.Item;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ItemTraitCatalogTest {

    private final ItemTraitCatalog catalog = new ItemTraitCatalog();

    @Test
    @DisplayName("SUPPORT 핵심 아이템 ID에 팀이 정의한 고유 trait를 제공한다")
    void traitsOf_ReturnsManuallyAssignedSupportTraits() {
        // given
        Item shurelyasBattlesong = new Item(2065L, Map.of("ko-KR", "슈렐리아의 군가"));
        Item locketOfTheIronSolari = new Item(3190L, Map.of("ko-KR", "강철의 솔라리 펜던트"));
        Item moonstoneRenewer = new Item(6617L, Map.of("ko-KR", "월석 재생기"));
        Item ardentCenser = new Item(3504L, Map.of("ko-KR", "불타는 향로"));

        // when & then
        // 이 테스트의 관심사는 "고유 trait가 제공되는가"이므로 그것만 확인한다.
        assertThat(catalog.traitsOf(shurelyasBattlesong)).contains(SHURELYA_BATTLESONG);
        assertThat(catalog.traitsOf(locketOfTheIronSolari)).contains(LOCKET_OF_THE_IRON_SOLARI);
        assertThat(catalog.traitsOf(moonstoneRenewer)).contains(MOONSTONE_RENEWER);
        assertThat(catalog.traitsOf(ardentCenser)).contains(ARDENT_CENSER);
    }

    @Test
    @DisplayName("수동 trait가 등록되지 않은 아이템은 빈 집합을 반환한다")
    void traitsOf_WhenItemIsNotRegistered_ReturnsEmptySet() {
        // given
        Item item = new Item(1L, Map.of("ko-KR", "등록되지 않은 아이템"));

        // when
        var traits = catalog.traitsOf(item);

        // then
        assertThat(traits).isEmpty();
    }
}
