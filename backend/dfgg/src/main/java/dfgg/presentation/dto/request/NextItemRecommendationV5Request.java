package dfgg.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dfgg.application.recommend.v3.ReportedGameState;
import dfgg.presentation.dto.ChampionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * v4 요청과 같고, 각 선수 항목에 지금의 상태(레벨, 킬·데스·어시스트, 들고 있는 아이템)를 더 받는다.
 * <p>
 * 상태 값은 모두 필수다. 게임 상황까지 읽는 새 모델은 상황 없는 입력을 거의 배우지 못했다.
 * <p>
 * 필드마다 붙는 검증은 v4 요청과 같은 것을 여기에도 적었다. 선수 항목의 타입이 달라 그대로 물려받을 수 없다.
 * 여러 필드를 함께 보는 검증 둘(구매 Core 개수, 보유 아이템 중복)은 v4 요청의 메서드를 그대로 부른다.
 * 두 요청의 규칙이 어긋나면 테스트가 알린다(검증을 통과한 요청의 v4 모양은 v4 검증도 통과해야 한다).
 */
public record NextItemRecommendationV5Request(
        @Valid
        @NotNull(message = "내 챔피언 정보는 필수입니다")
        MyChampionStateRequest myChampion,

        @NotNull(message = "구매한 Core 아이템 목록은 필수입니다(없으면 빈 배열)")
        List<@NotNull @Positive @Max(Integer.MAX_VALUE) Long> purchasedItemIds,

        @NotNull(message = "아군 정보는 필수입니다")
        @Size(min = 4, max = 4, message = "아군은 4명이어야 합니다")
        List<@NotNull @Valid PlayerStateRequest> allies,

        @NotNull(message = "적군 정보는 필수입니다")
        @Size(min = 5, max = 5, message = "적군은 5명이어야 합니다")
        List<@NotNull @Valid PlayerStateRequest> enemies,

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
    /**
     * 상태를 뺀 v4 요청 모양. 조합 아이템과 상황 대응 아이템은 v4와 같은 흐름을 타므로 이 모양으로 넘긴다.
     * <p>
     * 검증에서도 부르므로 필수값이 빠진 요청이 들어올 수 있다. 빠진 것은 빠진 채로 옮긴다.
     */
    public NextItemRecommendationV4Request toV4Request() {
        return new NextItemRecommendationV4Request(
                myChampion == null ? null : myChampion.toChampion(),
                purchasedItemIds, championsOf(allies), championsOf(enemies),
                tier, patch, inventoryItems, availableGold, gameTimeMs);
    }

    /** 10명의 상태. 아군과 상대는 요청의 선수 순서 그대로다. 검증을 통과한 요청에서만 부른다. */
    public ReportedGameState toGameState() {
        return new ReportedGameState(myChampion.toMyState(), statesOf(allies), statesOf(enemies));
    }

    @JsonIgnore
    @AssertTrue(message = "구매한 Core 아이템은 BOTTOM 7개, 그 외 포지션 6개까지입니다")
    public boolean isPurchasedItemCountWithinLimit() {
        return toV4Request().isPurchasedItemCountWithinLimit();
    }

    @JsonIgnore
    @AssertTrue(message = "보유 아이템 ID는 중복할 수 없습니다. 수량으로 표현해주세요")
    public boolean isInventoryItemIdsUnique() {
        return toV4Request().isInventoryItemIdsUnique();
    }

    private static List<ChampionDto> championsOf(List<PlayerStateRequest> players) {
        if (players == null) {
            return null;
        }
        return players.stream()
                .map(player -> player == null ? null : player.toChampion())
                .toList();
    }

    private static List<ReportedGameState.Player> statesOf(List<PlayerStateRequest> players) {
        return players.stream()
                .map(PlayerStateRequest::toReportedPlayer)
                .toList();
    }
}
