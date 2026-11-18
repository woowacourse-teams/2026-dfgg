package dfgg.presentation.dto.request;

import dfgg.application.recommend.v3.MyState;
import dfgg.presentation.dto.ChampionDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 새 추천 API의 내 챔피언 항목. v4의 이름·포지션에 지금의 레벨과 킬·데스·어시스트를 더 받는다.
 * <p>
 * 레벨에 상한을 두지 않는다. 게임이 레벨 상한을 바꾸면 요청이 전부 400이 되기 때문이다.
 */
public record MyChampionStateRequest(
        @NotBlank(message = "챔피언 이름은 필수입니다")
        String name,

        @NotBlank(message = "포지션은 필수입니다")
        @Pattern(regexp = "^(TOP|JUNGLE|MID|BOTTOM|SUPPORT)$", message = "올바른 포지션 형식이 아닙니다 (TOP, JUNGLE, MID, BOTTOM, SUPPORT 중 하나)")
        String position,

        @NotNull(message = "레벨은 필수입니다")
        @Min(value = 1, message = "레벨은 1 이상이어야 합니다")
        Integer level,

        @NotNull(message = "킬 수는 필수입니다")
        @PositiveOrZero(message = "킬 수는 0 이상이어야 합니다")
        Integer kills,

        @NotNull(message = "데스 수는 필수입니다")
        @PositiveOrZero(message = "데스 수는 0 이상이어야 합니다")
        Integer deaths,

        @NotNull(message = "어시스트 수는 필수입니다")
        @PositiveOrZero(message = "어시스트 수는 0 이상이어야 합니다")
        Integer assists
) {
    public ChampionDto toChampion() {
        return new ChampionDto(name, position);
    }

    public MyState toMyState() {
        return new MyState(level, kills, deaths, assists);
    }
}
