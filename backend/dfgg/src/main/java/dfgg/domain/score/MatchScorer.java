package dfgg.domain.score;

/**
 * 게임 모드 하나의 채점 규칙. 협곡은 포지션별 기준으로, 칼바람은 10명끼리 비교한다.
 */
public interface MatchScorer {

    boolean supports(int queueId);

    /** 공통 검증({@link ScoringParticipants})을 통과한 참가자에 모드 전용 규칙을 더 따진다. 어기면 예외를 던진다. */
    void validate(ScoringParticipants participants);
}
