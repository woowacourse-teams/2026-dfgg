package dfgg.presentation.dto.request;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;

/**
 * 상대 픽 분석 요청. {@code allies}·{@code bans}는 생략할 수 있다.
 */
public record CounterRequest(
        ChampionPosition myPosition,
        List<Integer> enemies,
        List<Integer> allies,
        List<Integer> bans
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
