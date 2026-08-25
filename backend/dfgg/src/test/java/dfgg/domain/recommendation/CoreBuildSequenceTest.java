package dfgg.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import dfgg.domain.item.Item;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class CoreBuildSequenceTest {

    @Test
    @DisplayName("신발을 제외하고 구매 순서대로 첫 3코어를 추출한다.")
    void create_ExcludingBoots_ReturnsFirstThreeCoreItems() {
        // given
        Item boots = new Item(1000L, "신발", List.of("Boots"));
        Item itemA = new Item(1001L, "아이템 A");
        Item itemB = new Item(1002L, "아이템 B");
        Item itemC = new Item(1003L, "아이템 C");
        Item itemD = new Item(1004L, "아이템 D");

        List<Item> purchaseOrder = List.of(
                boots,
                itemA,
                itemB,
                itemC,
                itemD
        );

        // when
        CoreBuildSequence sequence = CoreBuildSequence.from(purchaseOrder).orElseThrow();

        // then
        assertThat(sequence.getOrderedItems())
                .containsExactly(itemA, itemB, itemC);
    }

    @Test
    @DisplayName("신발을 제외한 코어 아이템이 3개 미만이면 생성하지 않는다")
    void create_WhenCoreItemsAreLessThanThree_ReturnsEmpty() {
        // given
        Item boots = new Item(1000L, "신발", List.of("Boots"));
        Item itemA = new Item(1001L, "아이템 A");
        Item itemB = new Item(1002L, "아이템 B");

        // when
        var result = CoreBuildSequence.from(
                List.of(boots, itemA, itemB)
        );

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("첫 3코어의 구매 순서가 달라도 같은 군집 키를 생성한다")
    void clusterKey_WhenItemOrderIsDifferent_ReturnsSameKey() {
        // given
        Item itemA = new Item(1001L, "아이템 A");
        Item itemB = new Item(1002L, "아이템 B");
        Item itemC = new Item(1003L, "아이템 C");

        // when
        CoreBuildSequence first = CoreBuildSequence.from(List.of(itemA, itemB, itemC)).orElseThrow();

        CoreBuildSequence second = CoreBuildSequence.from(List.of(itemB, itemA, itemC)).orElseThrow();

        // then
        assertThat(first.getOrderedItems())
                .containsExactly(itemA, itemB, itemC);

        assertThat(second.getOrderedItems())
                .containsExactly(itemB, itemA, itemC);

        assertThat(first.clusterKey())
                .isEqualTo(second.clusterKey());
    }

    @Test
    @DisplayName("첫 3코어의 아이템 구성이 다르면 다른 군집 키를 생성한다")
    void clusterKey_WhenCoreItemsAreDifferent_ReturnsDifferentKey() {
        // given
        Item itemA = new Item(1001L, "아이템 A");
        Item itemB = new Item(1002L, "아이템 B");
        Item itemC = new Item(1003L, "아이템 C");
        Item itemD = new Item(1004L, "아이템 D");

        CoreBuildSequence first = CoreBuildSequence.from(
                List.of(itemA, itemB, itemC)
        ).orElseThrow();

        CoreBuildSequence second = CoreBuildSequence.from(
                List.of(itemA, itemB, itemD)
        ).orElseThrow();

        // when
        List<Long> firstKey = first.clusterKey();
        List<Long> secondKey = second.clusterKey();

        // then
        assertThat(firstKey).isNotEqualTo(secondKey);
    }
}
