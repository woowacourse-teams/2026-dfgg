package dfgg.presentation.dto.request;

import dfgg.presentation.dto.ChampionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** 룬은 맞라인 상대를 보지 않으므로 내 챔피언·포지션만 받는다. */
public record RuneRecommendationRequest(
        @Valid
        @NotNull(message = "내 챔피언 정보는 필수입니다")
        ChampionDto myChampion
) {
}
