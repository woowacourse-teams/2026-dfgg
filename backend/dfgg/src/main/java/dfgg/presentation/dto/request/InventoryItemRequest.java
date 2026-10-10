package dfgg.presentation.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InventoryItemRequest(
        @NotNull(message = "아이템 ID는 필수입니다")
        @Positive(message = "아이템 ID는 양수여야 합니다")
        @Max(value = Integer.MAX_VALUE, message = "아이템 ID가 지원 범위를 초과했습니다")
        Long itemId,

        @NotNull(message = "아이템 수량은 필수입니다")
        @Positive(message = "아이템 수량은 1 이상이어야 합니다")
        Integer quantity
) {
}
