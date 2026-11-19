package dfgg.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dfgg.domain.champion.ChampionPosition;
import dfgg.domain.match.GameQueue;
import dfgg.presentation.dto.ChampionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Core 구매 이력과 같은 시점의 현재 인벤토리·골드·시간을 구분해서 받는다.
 * 챔피언 이름·포지션과 팀 인원은 큐에 따라 {@link ValidTeamRoster}가 검증한다.
 */
@ValidTeamRoster
public record NextItemRecommendationV4Request(
        @NotNull(message = "내 챔피언 정보는 필수입니다")
        ChampionDto myChampion,

        @NotNull(message = "구매한 Core 아이템 목록은 필수입니다(없으면 빈 배열)")
        List<@NotNull @Positive @Max(Integer.MAX_VALUE) Long> purchasedItemIds,

        @NotNull(message = "아군 정보는 필수입니다")
        List<@NotNull ChampionDto> allies,

        @NotNull(message = "적군 정보는 필수입니다")
        List<@NotNull ChampionDto> enemies,

        String tier,
        String patch,

        @NotNull(message = "현재 보유 아이템 목록은 필수입니다(없으면 빈 배열)")
        List<@NotNull @Valid InventoryItemRequest> inventoryItems,

        @NotNull(message = "현재 보유 골드는 필수입니다")
        @PositiveOrZero(message = "현재 보유 골드는 0 이상이어야 합니다")
        Long availableGold,

        @NotNull(message = "게임 경과 시간은 필수입니다")
        @PositiveOrZero(message = "게임 경과 시간은 0 이상이어야 합니다")
        Long gameTimeMs,

        /** 선택. 훈련모드·사용자 설정 게임이면 인원·포지션·팀원 이름을 검증하지 않는다. 비우면 일반 게임이다. */
        Integer queueId
) {
    public NextItemRecommendationV4Request(ChampionDto myChampion, List<Long> purchasedItemIds, List<ChampionDto> allies,
            List<ChampionDto> enemies, String tier, String patch, List<InventoryItemRequest> inventoryItems,
            Long availableGold, Long gameTimeMs) {
        this(myChampion, purchasedItemIds, allies, enemies, tier, patch, inventoryItems, availableGold, gameTimeMs,
                null);
    }

    @JsonIgnore
    public GameQueue gameQueue() {
        return new GameQueue(queueId);
    }

    /** 훈련모드·사용자 설정 게임에서 포지션을 비우거나 라인이 아닌 값(NONE 등)으로 보냈다. 일반 게임은 검증에서 걸러진다. */
    @JsonIgnore
    public boolean isMyPositionUnknown() {
        return !ChampionPosition.isName(myChampion.position());
    }

    public NextItemRecommendationV4Request withMyPosition(ChampionPosition position) {
        return new NextItemRecommendationV4Request(new ChampionDto(myChampion.name(), position.name()),
                purchasedItemIds, allies, enemies, tier, patch, inventoryItems, availableGold, gameTimeMs, queueId);
    }

    public NextItemRecommendationV4Request withTeams(List<ChampionDto> allies, List<ChampionDto> enemies) {
        return new NextItemRecommendationV4Request(myChampion, purchasedItemIds, allies, enemies, tier, patch,
                inventoryItems, availableGold, gameTimeMs, queueId);
    }

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
