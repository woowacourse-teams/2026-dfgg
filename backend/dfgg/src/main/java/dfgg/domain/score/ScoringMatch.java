package dfgg.domain.score;

/**
 * 채점을 요청받은 경기의 형태. 큐는 {@link MatchScorers}가 따지고, 여기서는 모드와 무관한 조건만 본다.
 * 채점 대상이 아니면 오류가 아니라 점수 없이 응답한다.
 */
public class ScoringMatch {

    /** 모든 대상 큐의 최소 항복 시간. 다시하기(early surrender)도 여기서 걸러진다. */
    private static final int MINIMUM_DURATION_SECONDS = 900;
    private static final int FULL_GAME_PARTICIPANT_COUNT = 10;

    private final int gameDurationSeconds;
    private final int participantCount;

    public ScoringMatch(int gameDurationSeconds, int participantCount) {
        this.gameDurationSeconds = gameDurationSeconds;
        this.participantCount = participantCount;
    }

    public boolean isScorable() {
        return gameDurationSeconds >= MINIMUM_DURATION_SECONDS
                && participantCount == FULL_GAME_PARTICIPANT_COUNT;
    }
}
