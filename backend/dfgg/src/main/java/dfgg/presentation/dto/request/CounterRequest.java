package dfgg.presentation.dto.request;

import dfgg.domain.champion.ChampionPosition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 상대 픽 분석 요청. 목록은 모두 생략할 수 있고, 상대 픽이 없어도(블루 1픽) 거부하지 않는다. 아군은 나를 뺀 최대 4명이다.
 */
public record CounterRequest(
        @NotNull ChampionPosition myPosition,
        @Size(max = 5) List<@NotNull Integer> enemies,
        @Size(max = 4) List<@NotNull Integer> allies,
        @Size(max = 10) List<@NotNull Integer> bans
) {

    public CounterRequest {
        enemies = copyOrEmpty(enemies);
        allies = copyOrEmpty(allies);
        bans = copyOrEmpty(bans);
    }

    private static List<Integer> copyOrEmpty(List<Integer> championIds) {
        if (championIds == null) {
            return List.of();
        }
        return List.copyOf(championIds);
    }
}
