package dfgg.domain.item.trait;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.item.Item;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 카탈로그는 여섯 파일을 합쳐서 낸다.
 */
class ItemTraitCatalogMergeTest {

    private final ItemTraitCatalog catalog = new ItemTraitCatalog();

    @Test
    @DisplayName("어느 파일에도 없는 아이템은 빈 집합이다 — 137개를 다 매기기 전까지 대부분이 여기다")
    void traitsOf_WhenItemIsUnmapped_IsEmpty() {
        assertThat(catalog.traitsOf(new Item(1054L, Map.of("ko-KR", "도란의 방패")))).isEmpty();
    }

    @Test
    @DisplayName("서포터 파일의 고유 trait가 합친 뒤에도 그대로 나온다")
    void traitsOf_PreservesSupportTraitsAfterMerge() {
        assertThat(catalog.hasTrait(new Item(3504L, Map.of("ko-KR", "불타는 향로")), ItemTrait.ATTACK_SPEED_AND_DAMAGE_BUFF)).isTrue();
        assertThat(catalog.hasTrait(new Item(3222L, Map.of("ko-KR", "미카엘의 축복")), ItemTrait.CC_CLEANSE)).isTrue();
        assertThat(catalog.hasTrait(new Item(6617L, Map.of("ko-KR", "월석 재생기")), ItemTrait.HEAL_SHIELD_AMPLIFY)).isTrue();
    }

    @Test
    @DisplayName("매핑을 직접 주입하면 그것만 쓴다 — 테스트가 기본 어휘에 묶이지 않는다")
    void traitsOf_WhenCatalogIsCustom_UsesOnlyTheGivenMapping() {
        ItemTraitCatalog custom = new ItemTraitCatalog(
                java.util.Map.of(3190L, java.util.Set.of(ItemTrait.CC_CLEANSE)));

        assertThat(custom.traitsOf(new Item(3190L, Map.of("ko-KR", "강철의 솔라리 펜던트"))))
                .containsExactly(ItemTrait.CC_CLEANSE);
    }
}
