package dfgg.domain.counter;

import dfgg.domain.champion.ChampionPosition;

/**
 * 한 챔피언이 한 라인에 간 비율. {@code rate}는 소수 첫째 자리 %다.
 */
public record PositionShare(ChampionPosition position, double rate, int games) {
}
