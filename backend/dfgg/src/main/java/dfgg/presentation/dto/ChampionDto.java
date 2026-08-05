package dfgg.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChampionDto(
        @NotBlank(message = "챔피언 이름은 필수입니다")
        String name,
        
        @NotBlank(message = "포지션은 필수입니다")
        @Pattern(regexp = "^(TOP|JUNGLE|MID|BOTTOM|SUPPORT)$", message = "올바른 포지션 형식이 아닙니다 (TOP, JUNGLE, MID, BOTTOM, SUPPORT 중 하나)")
        String position
) {
}
