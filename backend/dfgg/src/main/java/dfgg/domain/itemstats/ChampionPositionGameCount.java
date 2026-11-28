package dfgg.domain.itemstats;

import dfgg.domain.champion.ChampionPosition;

/**
 * 챔피언, 포지션별 완료 빌드 판 수.
 */
public record ChampionPositionGameCount(Integer championId, ChampionPosition position, Integer gameCount) {
}
