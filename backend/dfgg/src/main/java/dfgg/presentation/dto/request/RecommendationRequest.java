package dfgg.presentation.dto.request;

import dfgg.presentation.dto.ChampionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RecommendationRequest(
        @Valid
        @NotNull(message = "내 챔피언 정보는 필수입니다")
        ChampionDto myChampion,
        
        @Valid
        @NotNull(message = "아군 정보는 필수입니다")
        @Size(min = 4, max = 4, message = "아군은 4명이어야 합니다")
        List<ChampionDto> allies,
        
        @Valid
        @NotNull(message = "적군 정보는 필수입니다")
        @Size(min = 5, max = 5, message = "적군은 5명이어야 합니다")
        List<ChampionDto> enemies
) {
}
