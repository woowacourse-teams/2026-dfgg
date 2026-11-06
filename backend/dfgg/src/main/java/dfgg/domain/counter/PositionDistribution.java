package dfgg.domain.counter;

import dfgg.domain.champion.ChampionPosition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 한 챔피언의 현재 패치 라인 분포. 다섯 라인을 모두 담고 비율 내림차순으로 놓는다.
 */
public class PositionDistribution {

    private static final int RATE_SCALE = 1;
    private static final Comparator<PositionShare> BY_RATE_THEN_POSITION =
            Comparator.comparingDouble(PositionShare::rate).reversed()
                    .thenComparing(PositionShare::position);

    private final Map<ChampionPosition, Integer> gamesByPosition;
    private final int totalGames;

    private PositionDistribution(Map<ChampionPosition, Integer> gamesByPosition) {
        this.gamesByPosition = new EnumMap<>(ChampionPosition.class);
        this.gamesByPosition.putAll(gamesByPosition);
        this.totalGames = gamesByPosition.values().stream().mapToInt(Integer::intValue).sum();
    }

    public static PositionDistribution of(Map<ChampionPosition, Integer> gamesByPosition) {
        return new PositionDistribution(gamesByPosition);
    }

    /** 기록이 없으면 빈 목록이다. 0경기인 라인은 0%로 채운다. */
    public List<PositionShare> shares() {
        if (totalGames == 0) {
            return List.of();
        }
        return Arrays.stream(ChampionPosition.values())
                .map(this::shareOf)
                .sorted(BY_RATE_THEN_POSITION)
                .toList();
    }

    private PositionShare shareOf(ChampionPosition position) {
        int games = gamesByPosition.getOrDefault(position, 0);
        return new PositionShare(position, rateOf(games), games);
    }

    private double rateOf(int games) {
        return BigDecimal.valueOf(games * 100L)
                .divide(BigDecimal.valueOf(totalGames), RATE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
