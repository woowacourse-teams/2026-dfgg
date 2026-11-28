package dfgg.domain.match;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** 한 경기의 상태 기록. 어느 시각 직전의 상태를 물으면 답한다. */
public final class MatchStateTimeline {

    private final List<Event> events;

    /**
     * 기록을 시각 순으로 늘어놓는다. 같은 시각이면 받은 순서를 지킨다.
     * Core 보유는 순서에 따라 결과가 달라서, Core 구매 순서 계산기와 같은 순서로 처리한다.
     */
    public MatchStateTimeline(List<Event> events) {
        this.events = events.stream()
                .sorted(Comparator.comparingLong(Event::timestamp))
                .toList();
    }

    /** 기준 시각보다 앞선 기록만 반영한다. 같은 시각의 기록은 넣지 않는다. */
    public MatchState before(long gameTimeMs) {
        Map<Integer, ParticipantState> states = new HashMap<>();
        for (Event event : events) {
            if (event.timestamp() < gameTimeMs) {
                event.applyTo(states);
            }
        }
        return new MatchState(states);
    }

    private static void change(
            Map<Integer, ParticipantState> states,
            int participantId,
            UnaryOperator<ParticipantState> change
    ) {
        states.compute(participantId, (id, state) -> change.apply(state == null ? ParticipantState.initial() : state));
    }

    /** 상태를 바꾸는 기록 한 건. 자기가 바꾸는 참가자의 상태에 자기를 반영한다. */
    public sealed interface Event permits Kill, LevelUp, CoreItemChanged {

        long timestamp();

        void applyTo(Map<Integer, ParticipantState> states);
    }

    /**
     * 처치 한 건. 처치한 쪽이 참가자가 아니면(killerId가 0 이하) 킬은 아무에게도 주지 않는다.
     */
    public record Kill(long timestamp, int killerId, int victimId, List<Integer> assistingParticipantIds)
            implements Event {

        public Kill {
            assistingParticipantIds = List.copyOf(assistingParticipantIds);
        }

        @Override
        public void applyTo(Map<Integer, ParticipantState> states) {
            if (killerId > 0) {
                change(states, killerId, ParticipantState::killed);
            }
            change(states, victimId, ParticipantState::died);
            for (int assistant : assistingParticipantIds) {
                change(states, assistant, ParticipantState::assisted);
            }
        }
    }

    /** 레벨업 한 건. 참가자가 이 시각에 도달한 레벨을 담는다. */
    public record LevelUp(long timestamp, int participantId, int level) implements Event {

        @Override
        public void applyTo(Map<Integer, ParticipantState> states) {
            change(states, participantId, state -> state.leveledTo(level));
        }
    }

    /** 참가자의 Core 보유를 바꾸는 기록 한 건. 무엇이 빠지고 더해지는지는 {@link CoreItemChange}가 담는다. */
    public record CoreItemChanged(long timestamp, int participantId, CoreItemChange coreItemChange)
            implements Event {

        @Override
        public void applyTo(Map<Integer, ParticipantState> states) {
            change(states, participantId, state -> state.coreItemsChanged(coreItemChange));
        }
    }
}
