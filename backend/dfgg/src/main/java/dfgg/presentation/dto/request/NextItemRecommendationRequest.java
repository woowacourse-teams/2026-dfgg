package dfgg.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dfgg.domain.champion.ChampionPosition;
import dfgg.presentation.dto.ChampionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;

public record NextItemRecommendationRequest(
        @Valid
        @NotNull(message = "내 챔피언 정보는 필수입니다")
        ChampionDto myChampion,

        @NotNull(message = "구매한 아이템 목록은 필수입니다(아직 없으면 빈 배열)")
        List<@NotNull(message = "구매한 아이템 ID에 null이 들어갈 수 없습니다") Long> purchasedItemIds,

        @Valid
        @NotNull(message = "아군 정보는 필수입니다")
        @Size(min = 4, max = 4, message = "아군은 4명이어야 합니다")
        List<ChampionDto> allies,

        @Valid
        @NotNull(message = "적군 정보는 필수입니다")
        @Size(min = 5, max = 5, message = "적군은 5명이어야 합니다")
        List<ChampionDto> enemies,

        @NotBlank(message = "티어는 필수입니다")
        String tier,

        @NotBlank(message = "패치는 필수입니다")
        String patch
) {

    /**
     * 구매 아이템 수의 상한은 포지션마다 다르다(BOTTOM 7개, 그 외 6개). 포지션과 목록을 함께 봐야 해서
     * 필드 하나에 붙이지 못한다. 포지션이 비었거나 형식이 틀리면 여기서는 통과시킨다 — 그 사유는
     * {@code ChampionDto}의 검증이 따로 알린다.
     */
    @JsonIgnore
    @AssertTrue(message = "구매한 아이템은 BOTTOM 7개, 그 외 포지션 6개까지입니다")
    public boolean isPurchasedItemCountWithinLimit() {
        if (myChampion == null || purchasedItemIds == null) {
            return true;
        }
        return Arrays.stream(ChampionPosition.values())
                .filter(position -> position.name().equals(myChampion.position()))
                .findFirst()
                .map(position -> position.allowsPurchasedItemCount(purchasedItemIds.size()))
                .orElse(true);
    }
}
