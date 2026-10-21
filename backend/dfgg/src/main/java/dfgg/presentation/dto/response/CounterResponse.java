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

    public record PositionShareResponse(ChampionPosition position, double rate, int games) {
    }

    /** {@code reason}은 이유를 만들지 못했으면 null이다. */
    public record CounterPickResponse(Integer championId, String name, String reason) {
    }
}
