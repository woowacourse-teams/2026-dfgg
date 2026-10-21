package dfgg.presentation.dto.response;

import dfgg.domain.champion.ChampionPosition;
import java.util.List;

/**
 * 상대 픽 분석 응답. {@code patch}는 라인 분포의 기준 패치이고, 집계가 없으면 null이다.
 */
public record CounterResponse(String patch, ChampionPosition myPosition, List<EnemyCounterResponse> enemies) {

    public CounterResponse {
        enemies = List.copyOf(enemies);
    }

    public record EnemyCounterResponse(
            Integer championId,
            String name,
            List<PositionShareResponse> positions,
            List<CounterPickResponse> counters
    ) {

        public EnemyCounterResponse {
            positions = List.copyOf(positions);
            counters = List.copyOf(counters);
        }
    }

    /** {@code rate}는 소수 첫째 자리 %다. */
    public record PositionShareResponse(ChampionPosition position, double rate) {
    }

    public record CounterPickResponse(Integer championId, String name) {
    }
}
