package dfgg.domain.match;

import java.util.Map;

/** 어느 시각 직전의 경기 상태. 참가자 번호로 그 참가자의 상태를 답한다. */
public final class MatchState {

    private final Map<Integer, ParticipantState> states;

    MatchState(Map<Integer, ParticipantState> states) {
        this.states = Map.copyOf(states);
    }

    /** 기록에 한 번도 나오지 않은 참가자는 처음 상태로 답한다. */
    public ParticipantState of(int participantId) {
        return states.getOrDefault(participantId, ParticipantState.initial());
    }
}
