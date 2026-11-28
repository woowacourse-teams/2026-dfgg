package dfgg.domain.score;

import java.util.List;

/**
 * 게임 모드 하나의 채점 규칙. 협곡은 포지션별 기준으로, 칼바람은 10명끼리 비교한다.
 */
public interface MatchScorer {

    boolean supports(int queueId);

    /** 모드마다 최소 게임 시간이 다르다. 짧으면 채점하지 않는다. */
    boolean isLongEnough(GameDuration duration);

    /** 공통 검증({@link ScoringParticipants})을 통과한 참가자에 모드 전용 규칙을 더 따진다. 어기면 예외를 던진다. */
    void validate(ScoringParticipants participants);

    /** 참가자 순서대로 점수를 매긴다. 모드 전용 규칙을 어기면 예외를 던진다. */
    List<DfScore> score(ScoringParticipants participants, GameDuration duration);
}
