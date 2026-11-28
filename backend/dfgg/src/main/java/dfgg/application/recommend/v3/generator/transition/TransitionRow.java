package dfgg.application.recommend.v3.generator.transition;

/**
 * Build 전이 집계 한 줄: 이 전개 다음에 {@code itemId}를 산 표본 수와 그중 이긴 수(전체, 최근 패치).
 */
public record TransitionRow(long itemId, int supportAll, int winAll, int supportRecent, int winRecent) {

    /** 저장소가 돌려주는 {@code [item_id, support_all, win_all, support_recent, win_recent]}를 읽는다. */
    public static TransitionRow from(Object[] row) {
        return new TransitionRow(
                Long.parseLong((String) row[0]),
                intAt(row, 1),
                intAt(row, 2),
                intAt(row, 3),
                intAt(row, 4)
        );
    }

    private static int intAt(Object[] row, int index) {
        return ((Number) row[index]).intValue();
    }
}
