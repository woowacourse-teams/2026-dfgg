package dfgg.domain.score;

/**
 * 채점을 요청받은 경기의 형태. 큐와 최소 게임 시간은 모드마다 달라 {@link MatchScorer}가 따진다.
 * 채점 대상이 아니면 오류가 아니라 점수 없이 응답한다.
 */
public class ScoringMatch {

    private static final int FULL_GAME_PARTICIPANT_COUNT = 10;

    private final int participantCount;

    public ScoringMatch(int participantCount) {
        this.participantCount = participantCount;
    }

    public boolean isFullGame() {
        return participantCount == FULL_GAME_PARTICIPANT_COUNT;
    }
}
