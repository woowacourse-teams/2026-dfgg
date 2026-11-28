package dfgg.application.rune;

import java.util.ArrayList;
import java.util.List;

/**
 * 룬 정규화 한 배치에서 경기별 성공·실패를 기록하고 요약한다.
 * 모든 경기는 성공 또는 실패 중 하나로만 기록되므로 성공 수는 처리 수에서 실패 수를 빼서 구한다.
 */
class RuneNormalizationSummary {

    private final List<String> failures = new ArrayList<>();
    private int savedRunes;

    void succeed(int savedRunesForMatch) {
        savedRunes += savedRunesForMatch;
    }

    void fail(String matchId, String reason) {
        failures.add(matchId + ": " + reason);
    }

    RuneNormalizationBatchResult toBatchResult(int processed, String nextCursor, boolean hasMore) {
        return new RuneNormalizationBatchResult(processed, processed - failures.size(), failures.size(),
                savedRunes, nextCursor, hasMore, failures);
    }
}
