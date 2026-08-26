package dfgg.presentation.dto.request;

import dfgg.presentation.dto.ChampionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NextItemRecommendationRequest(
        @Valid
        @NotNull(message = "내 챔피언 정보는 필수입니다")
        ChampionDto myChampion,

        @NotNull(message = "구매한 아이템 목록은 필수입니다(아직 없으면 빈 배열)")
        List<Long> purchasedItemIds,

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
}
