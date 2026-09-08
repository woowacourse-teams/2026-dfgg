package dfgg.domain.item.trait;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.item.Item;
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
        assertThat(catalog.traitsOf(new Item(1054L, "도란의 방패"))).isEmpty();
    }

    @Test
    @DisplayName("v1/v2가 쓰는 유틸리티 trait가 그대로 유지된다")
    void traitsOf_PreservesUtilityTraitsUsedByBuildPolicy() {
        assertThat(catalog.hasTrait(new Item(3504L, "불타는 향로"), ItemTrait.TEAM_BUFF)).isTrue();
        assertThat(catalog.hasTrait(new Item(3222L, "미카엘의 축복"), ItemTrait.PEEL)).isTrue();
        assertThat(catalog.hasTrait(new Item(6617L, "월석 재생기"), ItemTrait.HEAL)).isTrue();
    }

    @Test
    @DisplayName("매핑을 직접 주입하면 그것만 쓴다 — 테스트가 기본 어휘에 묶이지 않는다")
    void traitsOf_WhenCatalogIsCustom_UsesOnlyTheGivenMapping() {
        ItemTraitCatalog custom = new ItemTraitCatalog(
                java.util.Map.of(3190L, java.util.Set.of(ItemTrait.ENGAGE)));

        assertThat(custom.traitsOf(new Item(3190L, "강철의 솔라리 펜던트")))
                .containsExactly(ItemTrait.ENGAGE);
    }
}
