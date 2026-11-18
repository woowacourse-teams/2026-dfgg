package dfgg.presentation.dto.request;

import dfgg.application.recommend.v3.ReportedGameState;
import dfgg.presentation.dto.ChampionDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 새 추천 API의 아군·상대 한 명. v4의 이름·포지션에 지금의 레벨, 킬·어시스트, 들고 있는 아이템을 더 받는다.
 * <p>
 * 데스는 받지 않는다. 아군·상대의 데스는 상황 값 어디에도 쓰이지 않는다.
 * <p>
 * {@code itemIds}는 들고 있는 아이템을 그대로 받는다. 장신구와 소모품이 섞여 있어도 된다. Core만 추리는 일은 서버가 한다.
 * 게임의 아이템 칸이 7개라 7개까지 받는다.
 */
public record PlayerStateRequest(
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

        @NotNull(message = "어시스트 수는 필수입니다")
        @PositiveOrZero(message = "어시스트 수는 0 이상이어야 합니다")
        Integer assists,

        @NotNull(message = "보유 아이템 목록은 필수입니다(없으면 빈 배열)")
        @Size(max = 7, message = "보유 아이템은 7개까지입니다")
        List<@NotNull @Positive @Max(Integer.MAX_VALUE) Long> itemIds
) {
    public ChampionDto toChampion() {
        return new ChampionDto(name, position);
    }

    public ReportedGameState.Player toReportedPlayer() {
        return new ReportedGameState.Player(level, kills, assists, itemIds);
    }
}
