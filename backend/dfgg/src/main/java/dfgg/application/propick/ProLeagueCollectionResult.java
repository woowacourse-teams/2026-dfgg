package dfgg.application.propick;

import java.util.Map;

/**
 * 리그 하나의 수집 결과. 실패했으면 {@code failure}에 사유가 있고 그 리그의 기존 데이터는 그대로다.
 */
public record ProLeagueCollectionResult(
        String league,
        int fetchedRows,
        int savedPicks,
        Map<ProPickSkipReason, Integer> skippedRows,
        String failure
) {

    public static ProLeagueCollectionResult collected(String league, int fetchedRows, int savedPicks,
                                                      Map<ProPickSkipReason, Integer> skippedRows) {
        return new ProLeagueCollectionResult(league, fetchedRows, savedPicks, Map.copyOf(skippedRows), null);
    }

    public static ProLeagueCollectionResult failed(String league, String failure) {
        return new ProLeagueCollectionResult(league, 0, 0, Map.of(), failure);
    }
}
