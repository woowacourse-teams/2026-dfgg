package dfgg.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dfgg.presentation.dto.ChampionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Core 구매 이력과 같은 시점의 현재 인벤토리·골드·시간을 구분해서 받는다. */
public record NextItemRecommendationV4Request(
        @Valid
        @NotNull(message = "내 챔피언 정보는 필수입니다")
        ChampionDto myChampion,

        @NotNull(message = "구매한 Core 아이템 목록은 필수입니다(없으면 빈 배열)")
        List<@NotNull @Positive @Max(Integer.MAX_VALUE) Long> purchasedItemIds,

        @NotNull(message = "아군 정보는 필수입니다")
        @Size(min = 4, max = 4, message = "아군은 4명이어야 합니다")
        List<@NotNull @Valid ChampionDto> allies,

        @NotNull(message = "적군 정보는 필수입니다")
        @Size(min = 5, max = 5, message = "적군은 5명이어야 합니다")
        List<@NotNull @Valid ChampionDto> enemies,

        String tier,
        String patch,

        @NotNull(message = "현재 보유 아이템 목록은 필수입니다(없으면 빈 배열)")
        List<@NotNull @Valid InventoryItemRequest> inventoryItems,

        @NotNull(message = "현재 보유 골드는 필수입니다")
        @PositiveOrZero(message = "현재 보유 골드는 0 이상이어야 합니다")
        Long availableGold,

        @NotNull(message = "게임 경과 시간은 필수입니다")
        @PositiveOrZero(message = "게임 경과 시간은 0 이상이어야 합니다")
        Long gameTimeMs
) {
    public NextItemRecommendationRequest toCoreRequest() {
        return new NextItemRecommendationRequest(myChampion, purchasedItemIds, allies, enemies, tier, patch);
    }

    @JsonIgnore
    @AssertTrue(message = "구매한 Core 아이템은 BOTTOM 7개, 그 외 포지션 6개까지입니다")
    public boolean isPurchasedItemCountWithinLimit() {
        return toCoreRequest().isPurchasedItemCountWithinLimit();
    }

    @JsonIgnore
    @AssertTrue(message = "보유 아이템 ID는 중복할 수 없습니다. 수량으로 표현해주세요")
    public boolean isInventoryItemIdsUnique() {
        if (inventoryItems == null) {
            return true;
        }
        Set<Long> seen = new HashSet<>();
        for (InventoryItemRequest item : inventoryItems) {
            // 필수값 누락은 각 필드의 @NotNull에서 처리한다.
            if (item != null && item.itemId() != null && !seen.add(item.itemId())) {
                return false;
            }
        }
        return true;
    }
}
