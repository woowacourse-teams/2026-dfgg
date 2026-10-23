package dfgg.presentation.dto.request;

import dfgg.domain.champion.ChampionPosition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 오늘의 추천 픽 요청. 밴은 생략할 수 있고, 양 팀이 같은 챔피언을 밴할 수 있어 중복을 허용한다.
 */
public record TodayPickRequest(
        @NotNull ChampionPosition position,
        @Size(max = 10) List<@NotNull Integer> bans
) {

    public TodayPickRequest {
        bans = copyOrEmpty(bans);
    }

    private static List<Integer> copyOrEmpty(List<Integer> championIds) {
        if (championIds == null) {
            return List.of();
        }
        return List.copyOf(championIds);
    }
}
