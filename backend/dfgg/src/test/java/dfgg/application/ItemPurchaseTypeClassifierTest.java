package dfgg.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dfgg.application.item.ItemPurchaseTypeClassifier;
import dfgg.domain.match.ItemPurchaseType;
import dfgg.infrastructure.external.dto.ItemResponse;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

class ItemPurchaseTypeClassifierTest {

    private final ItemPurchaseTypeClassifier classifier = new ItemPurchaseTypeClassifier();

    @Test
    void 실제_패치_카탈로그로_여섯_분류를_만든다() throws Exception {
        var result = classifier.classify(catalog().data());
        assertThat(result).containsEntry(1055, ItemPurchaseType.STARTER)
                .containsEntry(1082, ItemPurchaseType.STARTER)
                .containsEntry(1101, ItemPurchaseType.STARTER)
                .containsEntry(3865, ItemPurchaseType.STARTER)
                .containsEntry(2051, ItemPurchaseType.OTHER)
                .containsEntry(3112, ItemPurchaseType.OTHER)
                .containsEntry(3177, ItemPurchaseType.OTHER)
                .containsEntry(3184, ItemPurchaseType.OTHER)
                .containsEntry(1036, ItemPurchaseType.COMPONENT)
                .containsEntry(3133, ItemPurchaseType.COMPONENT)
                .containsEntry(3071, ItemPurchaseType.CORE)
                .containsEntry(3031, ItemPurchaseType.CORE)
                .containsEntry(3004, ItemPurchaseType.CORE)
                .containsEntry(1001, ItemPurchaseType.BOOTS)
                .containsEntry(3006, ItemPurchaseType.BOOTS)
                .containsEntry(2003, ItemPurchaseType.CONSUMABLE)
                .containsEntry(2031, ItemPurchaseType.CONSUMABLE)
                .containsEntry(2055, ItemPurchaseType.CONSUMABLE)
                .containsEntry(3340, ItemPurchaseType.OTHER)
                .containsEntry(3042, ItemPurchaseType.OTHER);
        assertThat(result).doesNotContainKey(999999);
        assertThatThrownBy(() -> result.put(999999, ItemPurchaseType.OTHER))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 참조_아이템이_누락되면_완성품으로_추측하지_않는다() throws Exception {
        var data = new HashMap<>(catalog().data());
        data.remove("1053");
        assertThatThrownBy(() -> classifier.classify(data))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("조합 대상");
    }

    @Test
    void 빈_카탈로그는_실패한다() {
        assertThatThrownBy(() -> classifier.classify(java.util.Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("카탈로그");
    }

    private ItemResponse catalog() throws Exception {
        try (var input = getClass().getResourceAsStream("/item/purchase-classification-16.18.1.json")) {
            return new ObjectMapper().readValue(input, ItemResponse.class);
        }
    }
}
